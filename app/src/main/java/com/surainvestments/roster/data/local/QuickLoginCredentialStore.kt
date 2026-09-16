package com.surainvestments.roster.data.local

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.time.Duration
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "quick_login_credential_key"
private const val GCM_TAG_LENGTH_BITS = 128
private val MANUAL_LOGIN_STALENESS: Duration = Duration.ofDays(7)

/**
 * Biometric quick-login — layer 2 on top of the existing app-lock gate ([DeviceAuthPreferences]).
 * Layer 1 only re-reveals an *already signed-in* session after backgrounding; this instead lets a
 * returning user skip retyping their password on the Login screen, by recalling it after a fresh
 * Class-3 biometric check.
 *
 * The password is encrypted with an AES-256-GCM key generated inside the Android Keystore with
 * `setUserAuthenticationRequired(true)` + `setUserAuthenticationValidityDurationSeconds(-1)` — the
 * latter means the key can be used *only* via a [Cipher] wrapped in a `BiometricPrompt.CryptoObject`
 * that has just been through a live biometric check; there is no way to encrypt or decrypt without
 * one, even from within this process. Callers must use the exact `Cipher` instance returned in
 * `BiometricPrompt.AuthenticationResult.cryptoObject` — a freshly re-initialized one would not be
 * authorized. `setInvalidatedByBiometricEnrollment(true)` means adding/removing a fingerprint/face
 * permanently invalidates the key; that's treated as "credential gone, fall back to password
 * login," never a crash (see [decryptCipher]).
 *
 * The **7-day manual-login staleness rule** (`ANDROID-STAFF-BUILD-PLAN.md` §2 Phase I) is enforced
 * here, not just in the UI: [isFresh] requires both a decryptable credential *and* a real password
 * login within the last 7 days, so quick-login can't silently keep working forever off one very old
 * password entry.
 *
 * Deliberately scoped to enable/disable from Account → Security only (a re-auth-confirmed toggle),
 * not also offered as a post-login prompt — the login flow's [AppRoute] resolves immediately on a
 * successful sign-in, leaving no stable screen to show a follow-up offer on without complicating
 * that routing state machine for a non-essential convenience prompt.
 */
@Singleton
class QuickLoginCredentialStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("quick_login", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    /** Non-secret — just which account a stored credential belongs to, safe to show before biometric unlock. */
    fun rememberedEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun hasStoredCredential(): Boolean = prefs.contains(KEY_CIPHERTEXT) && rememberedEmail() != null

    /** A decryptable credential *and* a recent-enough real password login. */
    fun isFresh(now: Instant = Instant.now()): Boolean {
        if (!hasStoredCredential()) return false
        val lastManual = prefs.getLong(KEY_LAST_MANUAL_LOGIN_EPOCH_MS, -1L)
        if (lastManual < 0) return false
        return Duration.between(Instant.ofEpochMilli(lastManual), now) <= MANUAL_LOGIN_STALENESS
    }

    /** Called after every successful *interactive* (password-typed) sign-in — resets the staleness clock. */
    fun markManualLoginNow(now: Instant = Instant.now()) {
        prefs.edit { putLong(KEY_LAST_MANUAL_LOGIN_EPOCH_MS, now.toEpochMilli()) }
    }

    /** An unauthorized encrypt-mode cipher — wrap in `BiometricPrompt.CryptoObject` and use only the post-auth instance with [save]. */
    fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher
    }

    /** Persists [email]/[password] using the already biometric-authorized [cipher] from `AuthenticationResult.cryptoObject`. */
    fun save(email: String, password: String, cipher: Cipher) {
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString(KEY_EMAIL, email)
            putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
        markManualLoginNow()
    }

    /**
     * An unauthorized decrypt-mode cipher — wrap in `BiometricPrompt.CryptoObject` and use only the
     * post-auth instance with [decryptPassword]. Null if nothing is stored, or if the Keystore key
     * was permanently invalidated (e.g. the device's biometric enrollment changed) — in the latter
     * case the stale entry is cleared so [hasStoredCredential] correctly reports false afterwards.
     */
    fun decryptCipher(): Cipher? {
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val key = getOrCreateKey()
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            clear()
            null
        }
    }

    /** Decrypts the stored password using the already biometric-authorized [cipher]. Null on any failure — always fall back to manual login, never crash the caller. */
    fun decryptPassword(cipher: Cipher): String? {
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return try {
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** Disables quick-login and wipes both the stored blob and the Keystore key — called on explicit disable, sign-out-and-forget, a changed password, or a permanently-invalidated key. */
    fun clear() {
        prefs.edit { clear() }
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Equivalent to the legacy "-1" below, plus restricts the key itself (not just the
            // BiometricPrompt call site) to Class-3/strong biometric — defense in depth.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private companion object {
        const val TRANSFORMATION =
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
        const val KEY_EMAIL = "email"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_IV = "iv"
        const val KEY_LAST_MANUAL_LOGIN_EPOCH_MS = "last_manual_login_epoch_ms"
    }
}
