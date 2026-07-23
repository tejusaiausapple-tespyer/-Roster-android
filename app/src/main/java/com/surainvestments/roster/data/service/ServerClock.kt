package com.surainvestments.roster.data.service

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Trusted wall-clock time, independent of the device's (user-changeable) clock.
 *
 * The offset is measured from the `Date` header of an HTTPS response from Google's Firestore
 * front-end — the same infrastructure that stamps the authoritative `FieldValue.serverTimestamp()`
 * values on attendance records. TLS prevents spoofing the header without also breaking the app's
 * Firebase traffic. Re-measure on login and every foreground activation by calling [sync].
 *
 * [now] falls back to the device clock until the first sync completes; callers that gate actions
 * on time should treat that as acceptable-but-unverified. Mirrors iOS `ServerClock`.
 */
@Singleton
class ServerClock @Inject constructor() {
    @Volatile private var offsetMillis: Long = 0
    @Volatile var isSynced: Boolean = false
        private set
    private val isSyncing = AtomicBoolean(false)

    private val client = OkHttpClient.Builder().build()
    private val headerFormatter = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US)

    /** Best-available current time: server-corrected when synced. */
    fun now(): Instant = Instant.now().plusMillis(offsetMillis)

    /** Measures the device-vs-server offset. Cheap (HEAD request); safe to call on every activation. */
    suspend fun sync() {
        if (!isSyncing.compareAndSet(false, true)) return
        try {
            withContext(Dispatchers.IO) {
                val before = Instant.now()
                val request = Request.Builder()
                    .url("https://firestore.googleapis.com/")
                    .head()
                    .cacheControl(CacheControl.Builder().noCache().noStore().build())
                    .build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        val header = response.header("Date") ?: return@use
                        val serverInstant = runCatching {
                            Instant.from(headerFormatter.parse(header))
                        }.getOrNull() ?: return@use
                        val after = Instant.now()
                        val midpoint = before.plusMillis((after.toEpochMilli() - before.toEpochMilli()) / 2)
                        offsetMillis = serverInstant.toEpochMilli() - midpoint.toEpochMilli()
                        isSynced = true
                    }
                }
            }
        } finally {
            isSyncing.set(false)
        }
    }
}
