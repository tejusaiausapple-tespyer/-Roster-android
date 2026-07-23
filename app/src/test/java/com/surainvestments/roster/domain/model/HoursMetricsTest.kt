package com.surainvestments.roster.domain.model

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ported from iOS's `HoursMetricsTests.swift` — same fixtures, same expected values. */
class HoursMetricsTest {

    // Fixed "now": Wednesday 3 June 2026, noon Adelaide.
    // Week = 2026-06-01 (Mon), month = June, year = 2026.
    private val now = LocalDate.parse("2026-06-03").atTime(LocalTime.NOON).atZone(RosterCalendar.zoneId).toInstant()

    private fun shift(id: String, date: String) = Shift(
        id = id,
        staffId = "staff-1",
        date = date,
        rosteredStart = "09:00",
        rosteredEnd = "17:00",
        breakMinutes = 0,
        scheduledHours = 0.0,
        location = null,
        department = null,
        notes = null,
        status = ShiftStatus.Published,
        submittableAfter = null,
        shiftStartAt = null,
    )

    private fun timesheet(
        id: String,
        shiftId: String,
        status: TimesheetStatus,
        workedHours: Double,
        submittedAt: java.time.Instant? = null,
    ) = Timesheet(
        id = id,
        shiftId = shiftId,
        staffId = "staff-1",
        actualStart = "",
        actualEnd = "",
        actualBreakMinutes = 0,
        workedHours = workedHours,
        staffNotes = null,
        status = status,
        managerNotes = null,
        approvedBy = null,
        approvedAt = null,
        rejectedReason = null,
        submittedAt = submittedAt,
        updatedAt = null,
    )

    private fun instant(dateKey: String, hhmm: String) =
        LocalDate.parse(dateKey).atTime(LocalTime.parse(hhmm)).atZone(RosterCalendar.zoneId).toInstant()

    private fun fixtures(): Pair<List<Shift>, List<Timesheet>> {
        val shiftThisWeek = shift(id = "s-week", date = "2026-06-02")
        val shiftLastMonth = shift(id = "s-may", date = "2026-05-05")
        val shifts = listOf(shiftThisWeek, shiftLastMonth)

        val approvedThisWeek = timesheet(id = "s-week", shiftId = "s-week", status = TimesheetStatus.Approved, workedHours = 5.0)
        val approvedLastMonth = timesheet(id = "s-may", shiftId = "s-may", status = TimesheetStatus.Approved, workedHours = 3.0)
        // Approved timesheet whose shift is NOT in the loaded shift list (left the ±28/56-day
        // listener window). Bucketing falls back to submittedAt — a same-year, different-month date.
        val approvedOrphan = timesheet(
            id = "s-old", shiftId = "s-old", status = TimesheetStatus.Approved, workedHours = 2.0,
            submittedAt = instant("2026-04-10", "18:00"),
        )
        val pending = timesheet(id = "s-pend", shiftId = "s-week", status = TimesheetStatus.Pending, workedHours = 4.0)
        val rejected = timesheet(id = "s-rej", shiftId = "s-week", status = TimesheetStatus.Rejected, workedHours = 1.0)

        return shifts to listOf(approvedThisWeek, approvedLastMonth, approvedOrphan, pending, rejected)
    }

    @Test
    fun `approved buckets by shift date`() {
        val (shifts, timesheets) = fixtures()
        val m = HoursMetrics.compute(timesheets, shifts, now)

        assertEquals("only the shift in the current Mon-start week", 5.0, m.week, 0.0)
        assertEquals("June only", 5.0, m.month, 0.0)
        assertEquals("June 5h + May 3h + April orphan 2h via submittedAt fallback", 10.0, m.year, 0.0)
        assertEquals("all approved hours incl. the orphan", 10.0, m.all, 0.0)
    }

    @Test
    fun `pending and rejected counts`() {
        val (shifts, timesheets) = fixtures()
        val m = HoursMetrics.compute(timesheets, shifts, now)

        assertEquals(4.0, m.pendingHours, 0.0)
        assertEquals(1, m.pendingCount)
        assertEquals(1, m.rejectedCount)
    }

    @Test
    fun `empty input`() {
        val m = HoursMetrics.compute(emptyList(), emptyList(), now)
        assertEquals(0.0, m.all, 0.0)
        assertEquals(0, m.pendingCount)
    }

    @Test
    fun `orphan fallback bucketing`() {
        val orphanSameMonth = timesheet(
            id = "o1", shiftId = "o1", status = TimesheetStatus.Approved, workedHours = 3.0,
            submittedAt = instant("2026-06-01", "17:30"), // this week + month
        )
        val orphanNoDate = timesheet(id = "o2", shiftId = "o2", status = TimesheetStatus.Approved, workedHours = 4.0) // no shift, no submittedAt

        val m = HoursMetrics.compute(listOf(orphanSameMonth, orphanNoDate), emptyList(), now)
        assertEquals(3.0, m.week, 0.0)
        assertEquals(3.0, m.month, 0.0)
        assertEquals(3.0, m.year, 0.0)
        assertEquals("undateable hours still count in the all-time total", 7.0, m.all, 0.0)
    }

    @Test
    fun `shift date preferred over submittedAt`() {
        val s = shift(id = "s1", date = "2026-05-05") // May
        val ts = timesheet(
            id = "s1", shiftId = "s1", status = TimesheetStatus.Approved, workedHours = 6.0,
            submittedAt = instant("2026-06-02", "10:00"), // submitted in June
        )

        val m = HoursMetrics.compute(listOf(ts), listOf(s), now)
        assertEquals("buckets to May (shift date), not June (submission)", 0.0, m.month, 0.0)
        assertEquals(6.0, m.year, 0.0)
    }
}
