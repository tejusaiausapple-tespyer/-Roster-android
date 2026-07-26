package com.surainvestments.roster.data.repository

import android.os.Build
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.surainvestments.roster.data.local.NotificationPreferences
import com.surainvestments.roster.data.remote.ActivateDeviceRequest
import com.surainvestments.roster.data.remote.WorkerApiService
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * `users/{uid}/notificationTokens/{urlEncodedToken}` — exact schema required by the deployed
 * rules' `isValidNotificationTokenData` (`token, platform, userAgent, enabled, createdAt,
 * updatedAt`, extra/missing keys rejected). `platform: "android-native"` is already whitelisted
 * server-side (`ANDROID-STAFF-BUILD-PLAN.md` §5) — no backend change needed.
 */
@Singleton
class NotificationTokenRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseMessaging: FirebaseMessaging,
    private val workerApi: WorkerApiService,
    private val notificationPreferences: NotificationPreferences,
) {
    /** Fetches the current FCM token and (re-)registers it — called on every login (idempotent, self-healing). */
    suspend fun registerCurrentToken(uid: String) {
        val token = firebaseMessaging.token.await()
        registerToken(uid, token)
    }

    /** Called directly from [com.surainvestments.roster.notifications.RosterMessagingService.onNewToken] when FCM
     *  rotates the token, and by [registerCurrentToken] above (login/resume heal). */
    suspend fun registerToken(uid: String, token: String) {
        writeTokenDoc(uid, token)

        // A silent FCM rotation on an already-registered device — never
        // fires on first-ever registration on this device (nothing stored
        // yet) or when the token is unchanged. Carries this device's
        // single-active-notification-device status (if any) forward to the
        // new token doc; a brand-new doc with no previous token to compare
        // against is left alone deliberately — the Worker's fail-open read
        // treats a missing `active` field as active, so a genuinely new
        // device is reachable by default until something else explicitly
        // deactivates it. Mirrors iOS's syncTokenAfterLogin and the PWA's
        // ensureNotificationRegistration.
        val previousToken = notificationPreferences.lastToken()
        if (previousToken != null && previousToken != token) {
            runCatching { activateDevice(token = token, previousToken = previousToken, reason = "refresh") }
        }
        notificationPreferences.setLastToken(token)
    }

    /**
     * Called right after a fresh sign-in succeeds ([AuthRepository.signIn]) —
     * distinct from [registerCurrentToken]/[registerToken], which also run
     * from [com.surainvestments.roster.notifications.PushTokenRegistrar]'s
     * reactive auth-state collector on a restored session, not just a
     * genuine login. Only a genuine login should claim this device as the
     * account's single active notification device — otherwise an unrelated
     * cold start could silently steal it back from wherever the account
     * most recently logged in. Writes the token doc itself first (rather
     * than trusting PushTokenRegistrar's concurrent registration to have
     * already finished) so the activation PATCH always has a doc to target.
     * No-ops if no token can be obtained.
     */
    suspend fun claimActiveDeviceOnLogin(uid: String) {
        val token = runCatching { firebaseMessaging.token.await() }.getOrNull() ?: return
        writeTokenDoc(uid, token)
        notificationPreferences.setLastToken(token)
        runCatching { activateDevice(token = token, previousToken = null, reason = "login") }
    }

    private suspend fun writeTokenDoc(uid: String, token: String) {
        val docRef = firestore.collection("users").document(uid)
            .collection("notificationTokens").document(URLEncoder.encode(token, "UTF-8"))

        // Preserve the original createdAt across re-registrations — only stamp it on first write.
        val alreadyExists = runCatching { docRef.get().await().exists() }.getOrDefault(false)

        val data = buildMap<String, Any> {
            put("token", token)
            put("platform", "android-native")
            put("userAgent", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL})")
            put("enabled", true)
            put("updatedAt", FieldValue.serverTimestamp())
            if (!alreadyExists) put("createdAt", FieldValue.serverTimestamp())
        }
        docRef.set(data, SetOptions.merge()).await()
    }

    /** POST /api/notifications/activate-device — best-effort, mirrors sendNotification's fire-and-forget shape. */
    private suspend fun activateDevice(token: String, previousToken: String?, reason: String) {
        workerApi.activateDevice(ActivateDeviceRequest(token = token, previousToken = previousToken, reason = reason))
    }
}
