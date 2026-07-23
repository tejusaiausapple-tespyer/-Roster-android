package com.surainvestments.roster.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-uid biometric app-lock enablement flag — the Android analogue of iOS's
 * `DeviceAuthService` Keychain flag (`roster_device_auth_{uid}`). This is a
 * local, non-server-verified on/off switch, not a stored credential, so plain
 * SharedPreferences (not EncryptedSharedPreferences) is an appropriate match
 * for the sensitivity level — same threat model as iOS storing a bare
 * existence-marker date string in Keychain.
 */
@Singleton
class DeviceAuthPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("device_auth", Context.MODE_PRIVATE)

    fun isEnabled(uid: String): Boolean = prefs.getBoolean(key(uid), false)

    fun setEnabled(uid: String, enabled: Boolean) {
        prefs.edit { putBoolean(key(uid), enabled) }
    }

    private fun key(uid: String) = "device_auth_enabled_$uid"
}
