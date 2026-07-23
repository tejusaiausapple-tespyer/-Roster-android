package com.surainvestments.roster.domain.model

import java.time.Duration
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ported from iOS's `BusinessRulesTests.swift` shift-instant cases. */
class ShiftTest {

    private fun shift(date: String, start: String, end: String) = Shift(
        id = "s1",
        staffId = "staff-1",
        date = date,
        rosteredStart = start,
        rosteredEnd = end,
        breakMinutes = 0,
        scheduledHours = 0.0,
        location = null,
        department = null,
        notes = null,
        status = ShiftStatus.Published,
        submittableAfter = null,
        shiftStartAt = null,
    )

    @Test
    fun `shift start date time components`() {
        val start = shift(date = "2026-03-10", start = "09:30", end = "17:00").startDateTime
        val zdt = ZonedDateTime.ofInstant(start, RosterCalendar.zoneId)
        assertEquals(2026, zdt.year)
        assertEquals(3, zdt.monthValue)
        assertEquals(10, zdt.dayOfMonth)
        assertEquals(9, zdt.hour)
        assertEquals(30, zdt.minute)
    }

    @Test
    fun `shift end same day`() {
        val end = shift(date = "2026-03-10", start = "09:00", end = "17:00").endDateTime
        val zdt = ZonedDateTime.ofInstant(end, RosterCalendar.zoneId)
        assertEquals(10, zdt.dayOfMonth)
        assertEquals(17, zdt.hour)
    }

    @Test
    fun `shift end crosses midnight`() {
        // end <= start means the shift finishes the next day
        val end = shift(date = "2026-03-10", start = "22:00", end = "06:00").endDateTime
        val zdt = ZonedDateTime.ofInstant(end, RosterCalendar.zoneId)
        assertEquals(11, zdt.dayOfMonth)
        assertEquals(6, zdt.hour)
    }

    @Test
    fun `shift instants across DST start are wall-clock anchored`() {
        // Adelaide DST ends 2026-04-05 03:00 (clocks back). An 8h wall-clock shift on that day
        // is 9h of absolute time -- verify the instants are wall-clock anchored, not
        // offset-anchored.
        val s = shift(date = "2026-04-05", start = "00:30", end = "08:30")
        val hours = Duration.between(s.startDateTime, s.endDateTime).seconds / 3600.0
        assertEquals("wall-clock 00:30-08:30 spans the repeated DST hour", 9.0, hours, 0.01)
    }
}
