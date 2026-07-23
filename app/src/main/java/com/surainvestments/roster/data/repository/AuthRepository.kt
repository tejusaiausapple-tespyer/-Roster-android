package com.surainvestments.roster.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.surainvestments.roster.data.remote.EmptyRequestBody
import com.surainvestments.roster.data.remote.WorkerApiService
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.AuthError
import com.surainvestments.roster.domain.model.UserStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    }

    fun signOut() {
        firebaseAuth.signOut()
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
            "ERROR_USER_NOT_FOUND", "ERROR_INVALID_EMAIL" -> AuthError.Generic("No account found for that email.")
            "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.Generic("Network error. Check your connection and try again.")
            "ERROR_TOO_MANY_REQUESTS" -> AuthError.Generic("Too many attempts. Please wait a moment and try again.")
            else -> AuthError.Generic(e.message ?: "Something went wrong. Please try again.")
        }
    }
}
