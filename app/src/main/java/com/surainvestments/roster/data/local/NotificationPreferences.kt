package com.surainvestments.roster.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the one-time notification-permission rationale has been shown, so the explainer
 * appears before the *first* POST_NOTIFICATIONS request and never again — subsequent logins request
 * silently (the OS de-dupes/auto-denies), matching iOS's "ask every login, explain once" flow.
 */
@Singleton
class NotificationPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    fun hasRequestedPermission(): Boolean = prefs.getBoolean(KEY_REQUESTED, false)

    fun markPermissionRequested() {
        prefs.edit { putBoolean(KEY_REQUESTED, true) }
    }

    /**
     * This device's most recently registered FCM token — lets
     * [com.surainvestments.roster.data.repository.NotificationTokenRepository]
     * detect a silent token rotation (the doc id is the token value itself,
     * so a rotation always produces a brand-new doc) and report it to the
     * Worker as "this replaces that", so single-active-device status carries
     * forward instead of silently resetting. Same purpose as iOS's
     * `roster_last_fcm_token` UserDefaults key and the PWA's localStorage
     * equivalent.
     */
    fun lastToken(): String? = prefs.getString(KEY_LAST_TOKEN, null)

    fun setLastToken(token: String) {
        prefs.edit { putString(KEY_LAST_TOKEN, token) }
    }

    private companion object {
        const val KEY_REQUESTED = "post_notifications_requested"
        const val KEY_LAST_TOKEN = "last_fcm_token"
    }
}
