package com.surainvestments.roster.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.data.local.QuickLoginCredentialStore
import com.surainvestments.roster.data.remote.EmptyRequestBody
import com.surainvestments.roster.data.remote.WorkerApiService
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.AuthError
import com.surainvestments.roster.domain.model.UserStatus
import com.surainvestments.roster.domain.model.friendlyMessage
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around Firebase Auth + the `users/{uid}` profile doc — the
 * Android analogue of iOS's `AuthService.swift` + the profile-validation half
 * of `AuthViewModel.login`. Session persistence is handled automatically by
 * the Firebase Android SDK, mirroring iOS's Keychain-backed persistence.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val workerApi: WorkerApiService,
    private val notificationTokenRepository: NotificationTokenRepository,
    private val quickLoginCredentialStore: QuickLoginCredentialStore,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    /** The signed-in uid right now, or null — for one-off writes that don't need a live flow. */
    fun currentUid(): String? = firebaseAuth.currentUser?.uid

    /** Emits the current uid every time Firebase Auth's session state changes (incl. immediately on subscribe). */
    fun authStateFlow(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser?.uid) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /** Live `users/{uid}` doc as a tolerant-parsed [AppUser], or null while missing/unparseable. */
    fun userProfileFlow(uid: String): Flow<AppUser?> = callbackFlow {
        val registration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                val data = snapshot?.data
                trySend(if (data != null) AppUser.fromDocument(uid, data) else null)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Sign in, then validate the profile/status exactly like the web/iOS
     * login flow: a locked/inactive/missing profile signs back out
     * immediately rather than landing the user on a half-authenticated screen.
     */
    suspend fun signIn(email: String, password: String) {
        val trimmed = email.trim()
        try {
            firebaseAuth.signInWithEmailAndPassword(trimmed, password).await()
        } catch (e: Exception) {
            throw mapAuthException(e)
        }

        val uid = firebaseAuth.currentUser?.uid ?: throw AuthError.NotAuthenticated
        val snapshot = try {
            firestore.collection("users").document(uid).get().await()
        } catch (e: Exception) {
            firebaseAuth.signOut()
            throw mapAuthException(e)
        }
        val data = snapshot.data
        if (data == null) {
            firebaseAuth.signOut()
            throw AuthError.ProfileNotFound
        }
        val user = AppUser.fromDocument(uid, data)
        if (user.status == UserStatus.Locked) {
            firebaseAuth.signOut()
            throw AuthError.AccountLocked
        }
        if (user.status == UserStatus.Inactive) {
            firebaseAuth.signOut()
            throw AuthError.AccountInactive
        }

        // Best-effort, deliberately launched on the app-wide scope rather
        // than awaited — claims this device as the account's single active
        // notification device without adding a network round trip to the
        // login flow (a plain `coroutineScope { launch {} }` here would
        // still suspend signIn() until the child completes, defeating the
        // point). Distinct from PushTokenRegistrar's reactive registration
        // (also runs on a restored session, not just a genuine login): only
        // this path should claim active status.
        appScope.launch { runCatching { notificationTokenRepository.claimActiveDeviceOnLogin(uid) } }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    /**
     * Staff self-service profile update — writes only the fields the deployed rules'
     * `isValidSelfUserUpdate` allow-list permits (`Roster PWA/firestore.rules`:
     * `fullName, phone, dob, address, emergencyContact, theme, profileUpdateRequired, updatedAt,
     * lastLoginAt, email, emailChangeRequired`). Any other key in the same `update()` call would
     * fail the rules' `hasOnly(allowedKeys)` check and reject the whole write.
     *
     * `emergencyContact` is a single free-text field ("Name & phone", matching the PWA's own
     * `EditStaffModal`/`AddStaffModal` shape) — not the separate `emergencyContactName/Phone/
     * Address/Email` keys [AppUser] also reads for backward compatibility; those aren't part of
     * the real deployed schema and would never round-trip through this allow-list.
     */
    suspend fun updateProfile(
        uid: String,
        fullName: String,
        phone: String,
        dob: String,
        address: String,
        emergencyContact: String,
        clearProfileUpdateRequired: Boolean,
    ) {
        val fields = mutableMapOf<String, Any>(
            "fullName" to fullName.trim(),
            "phone" to phone.trim(),
            "dob" to dob.trim(),
            "address" to address.trim(),
            "emergencyContact" to emergencyContact.trim(),
            "updatedAt" to Instant.now().toString(),
        )
        if (clearProfileUpdateRequired) fields["profileUpdateRequired"] = false
        firestore.collection("users").document(uid).update(fields).await()
    }

    /**
     * Re-authenticate with the current password, then set the new one —
     * mirrors iOS's `AuthService.changePassword`. If [wasForced] (the profile
     * had `mustChangePassword == true`), also clears that flag server-side via
     * the Worker's `/api/complete-password-change`, which requires the ID
     * token to already reflect the just-changed password — force-refresh it
     * first rather than relying on the auth interceptor's cached copy.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String, wasForced: Boolean) {
        val user = firebaseAuth.currentUser ?: throw AuthError.NotAuthenticated
        val email = user.email ?: throw AuthError.NotAuthenticated
        try {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            user.getIdToken(true).await()
        } catch (e: Exception) {
            throw mapAuthException(e)
        }

        if (wasForced) {
            runCatching { workerApi.completePasswordChange(EmptyRequestBody) }
        }

        // The stored quick-login credential (if any) now holds a password that no longer works —
        // clear it rather than leave a foot-gun that fails confusingly on the next quick-login
        // attempt. The user can re-enable it from Account → Security with the new password.
        quickLoginCredentialStore.clear()
    }

    /**
     * Re-authenticate with the current password, then request the email change via Firebase
     * Auth's own **verified** flow (`verifyBeforeUpdateEmail`) — mirrors iOS's `ChangeEmailView`
     * / `AuthService.changeEmail`. Deliberately does **not** use the older `updateEmail`, which
     * changes the credential immediately with no confirmation step: `verifyBeforeUpdateEmail`
     * sends a link to the *new* address and leaves the current, already-verified email fully
     * functional until the staff member clicks it — so a wrong/mistyped/inaccessible new address
     * can never lock anyone out, it just leaves the request unconfirmed.
     *
     * Firestore's `users/{uid}.email` is a **display copy only** (`Roster PWA/firestore.rules`'s
     * `isValidSelfUserUpdate` comment) — the real credential lives in Firebase Auth and only
     * actually changes once the staff member confirms the link, at some later, unpredictable
     * time (possibly a different session entirely). That reconciliation happens in
     * [syncEmailIfChanged], not here.
     */
    suspend fun changeEmail(currentPassword: String, newEmail: String) {
        val user = firebaseAuth.currentUser ?: throw AuthError.NotAuthenticated
        val currentEmail = user.email ?: throw AuthError.NotAuthenticated
        try {
            val credential = EmailAuthProvider.getCredential(currentEmail, currentPassword)
            user.reauthenticate(credential).await()
            user.verifyBeforeUpdateEmail(newEmail.trim()).await()
        } catch (e: Exception) {
            throw mapAuthException(e)
        }
    }

    /**
     * Best-effort reconciliation for [changeEmail]'s deferred confirmation: if Firebase Auth's
     * own (verified) email no longer matches what's stored on the profile doc, the staff member
     * must have clicked the confirmation link since the doc was last written — sync the display
     * copy so the rest of the app (which reads the Firestore doc, not `FirebaseAuth` directly)
     * shows the current address. Called opportunistically on profile changes; never throws —
     * a missed sync just means the display copy stays one login behind, not a functional problem
     * since Firebase Auth itself is always the real credential either way.
     */
    suspend fun syncEmailIfChanged(uid: String, storedEmail: String) {
        val user = firebaseAuth.currentUser ?: return
        val authEmail = user.email ?: return
        if (!user.isEmailVerified || authEmail == storedEmail) return
        runCatching {
            firestore.collection("users").document(uid)
                .update(mapOf("email" to authEmail, "emailChangeRequired" to false, "updatedAt" to Instant.now().toString()))
                .await()
        }
    }

    /**
     * Confirms [password] is actually correct for the signed-in user via Firebase's own
     * reauthenticate call — the same mechanism [changePassword] already relies on. Used to gate
     * enabling quick-login (the biometric credential store must only ever be seeded with a
     * password Firebase itself just verified, never one accepted on faith from the caller).
     */
    suspend fun verifyPassword(password: String): Boolean {
        val user = firebaseAuth.currentUser ?: return false
        val email = user.email ?: return false
        return try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendPasswordReset(email: String) {
        try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
        } catch (e: Exception) {
            throw mapAuthException(e)
        }
    }

    /** Mirrors iOS's `mapAuthError` switch on `AuthErrorCode`, using Android's equivalent `errorCode` strings. */
    private fun mapAuthException(e: Exception): AuthError {
        val code = (e as? FirebaseAuthException)?.errorCode
        return when (code) {
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_MISMATCH" -> AuthError.WrongPassword
            "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword
            "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_CREDENTIAL_ALREADY_IN_USE" -> AuthError.Generic("That email address is already in use.")
            "ERROR_USER_NOT_FOUND", "ERROR_INVALID_EMAIL" -> AuthError.Generic("No account found for that email.")
            "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.Generic("Network error. Check your connection and try again.")
            "ERROR_TOO_MANY_REQUESTS" -> AuthError.Generic("Too many attempts. Please wait a moment and try again.")
            else -> AuthError.Generic(friendlyMessage(e, "Something went wrong. Please try again."))
        }
    }
}
