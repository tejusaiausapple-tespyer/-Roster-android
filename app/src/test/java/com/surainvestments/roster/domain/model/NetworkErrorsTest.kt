package com.surainvestments.roster.domain.model

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

/** Confirms raw network exceptions never reach the user verbatim — see [friendlyMessage]'s doc comment. */
class NetworkErrorsTest {

    @Test
    fun `a timeout never shows its raw one-word message`() {
        val message = friendlyMessage(SocketTimeoutException("timeout"), "fallback")
        assertEquals("The connection timed out. Check your signal and try again.", message)
    }

    @Test
    fun `an unknown host never shows a raw hostname`() {
        val message = friendlyMessage(UnknownHostException("sura-roster.com"), "fallback")
        assertEquals("Couldn't reach the server. Check your internet connection.", message)
    }

    @Test
    fun `a generic IO failure gets a readable connectivity message`() {
        val message = friendlyMessage(java.io.IOException("Failed to connect to /1.2.3.4:443"), "fallback")
        assertEquals("Couldn't reach the server. Check your internet connection and try again.", message)
    }

    @Test
    fun `WorkerApiException's own message passes through unchanged`() {
        val message = friendlyMessage(WorkerApiException("That week is locked."), "fallback")
        assertEquals("That week is locked.", message)
    }

    @Test
    fun `anything else falls back to its own message, or the caller's fallback if it has none`() {
        assertEquals("custom", friendlyMessage(IllegalStateException("custom"), "fallback"))
        assertEquals("fallback", friendlyMessage(IllegalStateException(), "fallback"))
    }
}
