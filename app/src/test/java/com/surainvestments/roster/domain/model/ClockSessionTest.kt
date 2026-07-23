package com.surainvestments.roster.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from iOS's `ClockSessionTests.swift` (RosterraTests) — same named cases, same expected
 * values. Kotlin's [ClockSession] is immutable/copy-based rather than `mutating func`, so a
 * sequence of actions is a chain of reassignments (`s = s.startBreak(...)`) instead of in-place
 * mutation — the underlying math is identical.
 */
class ClockSessionTest {

    private val t0: Instant = Instant.ofEpochSecond(1_750_000_000)

    private fun session() = ClockSession(shiftId = "s1", staffId = "u1", clockInAt = t0)

    @Test
    fun `no break submits zero break`() {
        val s = session().clockOut(t0.plusSeconds(4 * 3600))
        assertEquals(0, s.timesheetBreakMinutes())
        assertEquals(4 * 3600L, s.workedSeconds())
        assertFalse(s.isActive)
    }

    @Test
    fun `break deducted from worked time`() {
        val s = session()
            .startBreak(t0.plusSeconds(3600))
            .endBreak(t0.plusSeconds(3600 + 30 * 60))
            .clockOut(t0.plusSeconds(8 * 3600))
        assertEquals(30, s.timesheetBreakMinutes())
        assertEquals((7.5 * 3600).toLong(), s.workedSeconds())
    }

    // ── Early check-in / paid time ───────────────────────────────────────

    @Test
    fun `early check-in not paid`() {
        val rosterStart = t0.plusSeconds(5 * 60)
        val s = session().clockOut(rosterStart.plusSeconds(4 * 3600))
        assertEquals(4 * 3600L, s.paidWorkedSeconds(rosterStart))
        assertEquals(rosterStart, s.paidStart(rosterStart))
        // The raw session still records the true 4h05m on-clock time.
        assertEquals(4 * 3600L + 5 * 60, s.workedSeconds())
    }

    @Test
    fun `late check-in paid from clock-in`() {
        val rosterStart = t0.minusSeconds(30 * 60) // clocked in 30m late
        val s = session().clockOut(t0.plusSeconds(2 * 3600))
        assertEquals(2 * 3600L, s.paidWorkedSeconds(rosterStart))
        assertEquals(t0, s.paidStart(rosterStart))
    }

    @Test
    fun `break during early window not double deducted`() {
        val rosterStart = t0.plusSeconds(10 * 60)
        val s = session()
            .startBreak(t0.plusSeconds(5 * 60)) // starts before roster start
            .endBreak(rosterStart.plusSeconds(10 * 60)) // ends 10m into paid time
            .clockOut(rosterStart.plusSeconds(3600))
        // Paid window is 1h; only 10m of the break overlaps it.
        assertEquals(50 * 60L, s.paidWorkedSeconds(rosterStart))
    }

    @Test
    fun `multiple breaks accumulate`() {
        val s = session()
            .startBreak(t0.plusSeconds(3600))
            .endBreak(t0.plusSeconds(3600 + 600)) // 10m
            .startBreak(t0.plusSeconds(3 * 3600))
            .endBreak(t0.plusSeconds(3 * 3600 + 900)) // 15m
        val now = t0.plusSeconds(4 * 3600)
        assertEquals(1500L, s.totalBreakSeconds(now))
        assertEquals(25, s.timesheetBreakMinutes(now))
    }

    @Test
    fun `break minutes round to step and clamp`() {
        val s = session().startBreak(t0).endBreak(t0.plusSeconds(23 * 60)) // 23m -> nearest 5 = 25
        assertEquals(25, s.timesheetBreakMinutes())

        val long = session().startBreak(t0).endBreak(t0.plusSeconds(2 * 3600)) // 120m -> clamp 90
        assertEquals(90, long.timesheetBreakMinutes())
    }

    @Test
    fun `clock-out ends open break`() {
        var s = session().startBreak(t0.plusSeconds(3600))
        assertTrue(s.isOnBreak)
        s = s.clockOut(t0.plusSeconds(3600 + 300))
        assertFalse(s.isOnBreak)
        assertEquals(300L, s.totalBreakSeconds())
    }

    @Test
    fun `start break ignored while on break or after clock-out`() {
        var s = session()
            .startBreak(t0.plusSeconds(60))
            .startBreak(t0.plusSeconds(120)) // ignored -- already on break
        assertEquals(1, s.breaks.size)
        s = s.clockOut(t0.plusSeconds(3600))
            .startBreak(t0.plusSeconds(3700)) // ignored -- clocked out
        assertEquals(1, s.breaks.size)
    }
}
