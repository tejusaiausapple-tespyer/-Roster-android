package com.surainvestments.roster.domain.model

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps a caught write-path exception to text that's actually safe to show a staff member.
 *
 * Every Worker-backed write (`AvailabilityRepository`, `AccountDeletionRepository`, profile
 * edits, etc.) currently does `catch (e: Exception) { errorMessage = e.message ?: fallback }`.
 * That's fine for [WorkerApiException] — its message is always either the server's own error
 * text or something we crafted ourselves — but a connectivity failure never reaches the server
 * at all, so it surfaces as a raw OkHttp/Java exception instead: `SocketTimeoutException`'s
 * message is literally the single word `"timeout"`, `UnknownHostException`'s is a bare hostname.
 * Confirmed live: a staff member on a weak signal saved Availability and saw a banner that just
 * said "timeout" — this function is what that banner should have shown instead.
 */
fun friendlyMessage(e: Throwable, fallback: String): String = when (e) {
    is WorkerApiException -> e.message ?: fallback
    is SocketTimeoutException -> "The connection timed out. Check your signal and try again."
    is UnknownHostException -> "Couldn't reach the server. Check your internet connection."
    is IOException -> "Couldn't reach the server. Check your internet connection and try again."
    else -> e.message ?: fallback
}
