package com.surainvestments.roster.domain.model

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from iOS's `BusinessRulesTests.swift` (RosterraTests) — same named cases, same
 * expected values, minus `payrollGaps` (manager-only, out of scope for the Staff app — see
 * `ANDROID-STAFF-BUILD-PLAN.md`). All `now` values are Australia/Adelaide wall-clock instants.
 */
class BusinessRulesTest {

    private fun instant(dateKey: String, hhmm: String) =
        LocalDate.parse(dateKey).atTime(LocalTime.parse(hhmm)).atZone(RosterCalendar.zoneId).toInstant()

    private fun shift(
        id: String = "s1",
        staffId: String = "staff-1",
        date: String,
        start: String = "09:00",
        end: String = "17:00",
        status: ShiftStatus = ShiftStatus.Published,
        submittableAfter: java.time.Instant? = null,
    ) = Shift(
        id = id,
        staffId = staffId,
        date = date,
        rosteredStart = start,
        rosteredEnd = end,
        breakMinutes = 0,
        scheduledHours = 0.0,
        location = null,
        department = null,
        notes = null,
        status = status,
        submittableAfter = submittableAfter,
        shiftStartAt = null,
    )

    private fun timesheet(status: TimesheetStatus, id: String = "s1", shiftId: String = "s1", staffId: String = "staff-1") =
        Timesheet(
            id = id,
            shiftId = shiftId,
            staffId = staffId,
            actualStart = "",
            actualEnd = "",
            actualBreakMinutes = 0,
            workedHours = 0.0,
            staffNotes = null,
            status = status,
            managerNotes = null,
            approvedBy = null,
            approvedAt = null,
            rejectedReason = null,
            submittedAt = null,
            updatedAt = null,
        )

    // ── Worked hours ──────────────────────────────────────────────────────

    @Test
    fun `calc worked hours standard`() {
        assertEquals(7.5, BusinessRules.calcWorkedHours("09:00", "17:00", 30), 0.0)
    }

    @Test
    fun `calc worked hours crosses midnight`() {
        assertEquals(8.0, BusinessRules.calcWorkedHours("22:00", "06:00", 0), 0.0)
    }

    @Test
    fun `calc worked hours rounding`() {
        assertEquals(0.33, BusinessRules.calcWorkedHours("09:00", "09:20", 0), 0.0)
    }

    @Test
    fun `calc worked hours break exceeds span`() {
        assertEquals(0.0, BusinessRules.calcWorkedHours("09:00", "09:15", 30), 0.0)
    }

    @Test
    fun `calc worked hours invalid input`() {
        assertEquals(0.0, BusinessRules.calcWorkedHours("garbage", "17:00", 0), 0.0)
    }

    @Test
    fun `clamp break minutes`() {
        assertEquals(0, BusinessRules.clampBreakMinutes(-5))
        assertEquals(45, BusinessRules.clampBreakMinutes(45))
        assertEquals(90, BusinessRules.clampBreakMinutes(120))
    }

    // ── Week selector bounds ─────────────────────────────────────────────

    @Test
    fun `shift week offset bounds from a monday`() {
        val now = instant("2026-06-01", "12:00") // Monday
        val bounds = BusinessRules.shiftWeekOffsetBounds(now)
        assertEquals("28 days = exactly 4 Mondays back", -4, bounds.first)
        assertEquals("56 days = exactly 8 Mondays forward", 8, bounds.last)
    }

    // ── Week lock ─────────────────────────────────────────────────────────

    @Test
    fun `week lock current and past locked, next editable`() {
        val now = instant("2026-06-03", "12:00") // Wed of week 2026-06-01
        assertTrue("current week locked", BusinessRules.isWeekLockedForStaff("2026-06-01", now))
        assertTrue("past week locked", BusinessRules.isWeekLockedForStaff("2026-05-25", now))
        assertFalse("next week editable", BusinessRules.isWeekLockedForStaff("2026-06-08", now))
    }

    @Test
    fun `manager availability week lock`() {
        val now = instant("2026-06-03", "12:00")
        val locked = setOf("2026-06-08")
        assertTrue("manager-locked future week is locked", BusinessRules.isWeekLockedForStaff("2026-06-08", locked, now))
        assertFalse("other future weeks stay editable", BusinessRules.isWeekLockedForStaff("2026-06-15", locked, now))
        assertTrue("current week still locked with no manager locks", BusinessRules.isWeekLockedForStaff("2026-06-01", emptySet(), now))
        assertFalse("unlocked future week editable", BusinessRules.isWeekLockedForStaff("2026-06-08", emptySet(), now))
    }

    @Test
    fun `recurring week keys span horizon`() {
        val now = instant("2026-06-01", "12:00") // Monday
        val keys = BusinessRules.recurringWeekKeys("2026-06-01", now)
        // Current Monday through +12 weeks inclusive = 13 keys
        assertEquals(13, keys.size)
        assertEquals("2026-06-01", keys.first())
        assertEquals("2026-08-24", keys.last())
    }

    // ── Display status & action gates ────────────────────────────────────

    private val shiftDay = "2026-06-02"
    private fun beforeShiftEnd() = instant(shiftDay, "12:00")
    private fun afterShiftEnd() = instant(shiftDay, "18:00")

    @Test
    fun `display status scheduled before end`() {
        val s = shift(date = shiftDay)
        assertEquals(StaffShiftDisplayStatus.Scheduled, BusinessRules.displayStatus(s, null, beforeShiftEnd()))
    }

    @Test
    fun `display status awaiting submission after end`() {
        val s = shift(date = shiftDay)
        assertEquals(StaffShiftDisplayStatus.AwaitingSubmission, BusinessRules.displayStatus(s, null, afterShiftEnd()))
    }

    @Test
    fun `display status mirrors timesheet`() {
        val s = shift(date = shiftDay)
        val approved = timesheet(TimesheetStatus.Approved)
        val absent = timesheet(TimesheetStatus.AbsentReported)
        assertEquals(StaffShiftDisplayStatus.Approved, BusinessRules.displayStatus(s, approved, afterShiftEnd()))
        assertEquals(StaffShiftDisplayStatus.AbsentReported, BusinessRules.displayStatus(s, absent, afterShiftEnd()))
    }

    @Test
    fun `can submit hours gates`() {
        val published = shift(date = shiftDay)
        val draft = shift(date = shiftDay, status = ShiftStatus.Draft)

        assertFalse(BusinessRules.canSubmitHours(published, null, beforeShiftEnd()))
        assertTrue(BusinessRules.canSubmitHours(published, null, afterShiftEnd()))

        assertFalse("drafts never submittable", BusinessRules.canSubmitHours(draft, null, afterShiftEnd()))

        assertTrue(BusinessRules.canSubmitHours(published, timesheet(TimesheetStatus.Rejected), afterShiftEnd()))
        assertTrue(
            "submitted hours stay editable until approved",
            BusinessRules.canSubmitHours(published, timesheet(TimesheetStatus.Pending), afterShiftEnd()),
        )
        assertFalse(BusinessRules.canSubmitHours(published, timesheet(TimesheetStatus.Approved), afterShiftEnd()))
        assertFalse(BusinessRules.canSubmitHours(published, timesheet(TimesheetStatus.Absent), afterShiftEnd()))
    }

    @Test
    fun `can report absence gates`() {
        val s = shift(date = shiftDay)
        assertFalse("time gate", BusinessRules.canReportAbsence(s, null, beforeShiftEnd()))
        assertTrue(BusinessRules.canReportAbsence(s, null, afterShiftEnd()))
        assertTrue(BusinessRules.canReportAbsence(s, timesheet(TimesheetStatus.Rejected), afterShiftEnd()))
        assertFalse(BusinessRules.canReportAbsence(s, timesheet(TimesheetStatus.Approved), afterShiftEnd()))
    }

    @Test
    fun `needs staff action`() {
        val s = shift(date = shiftDay)
        assertFalse(BusinessRules.needsStaffAction(s, null, beforeShiftEnd()))
        assertTrue("unsubmitted after end", BusinessRules.needsStaffAction(s, null, afterShiftEnd()))
        assertTrue(BusinessRules.needsStaffAction(s, timesheet(TimesheetStatus.Rejected), afterShiftEnd()))
        assertTrue("undoable absence", BusinessRules.needsStaffAction(s, timesheet(TimesheetStatus.AbsentReported), beforeShiftEnd()))
        assertFalse(BusinessRules.needsStaffAction(s, timesheet(TimesheetStatus.Approved), afterShiftEnd()))
    }

    @Test
    fun `submittableAfter overrides end time`() {
        val s = shift(date = shiftDay, submittableAfter = instant("2026-06-02", "20:00"))
        assertFalse("18:00 is before the 20:00 override", s.isSubmittable(afterShiftEnd()))
        assertTrue(s.isSubmittable(instant("2026-06-02", "20:01")))
    }

    // ── Email & password validation ──────────────────────────────────────

    @Test
    fun `email validation`() {
        assertTrue(BusinessRules.isValidEmail("a@b.com"))
        assertFalse(BusinessRules.isValidEmail("not-an-email"))
        assertFalse(BusinessRules.isValidEmail("a@b"))
        assertFalse(BusinessRules.isValidEmail(""))
    }

    @Test
    fun `password errors all missing`() {
        assertEquals(3, PasswordRules.errors("abc").size)
    }

    @Test
    fun `password valid without symbol`() {
        assertTrue(PasswordRules.errors("Abcdefg1").isEmpty())
    }

    @Test
    fun `password missing uppercase`() {
        assertEquals(listOf("One uppercase letter"), PasswordRules.errors("abcdefg1"))
    }

    @Test
    fun `password missing digit`() {
        assertEquals(listOf("One number"), PasswordRules.errors("Abcdefgh"))
    }

    @Test
    fun `password rules checklist shape`() {
        val rules = PasswordRules.rules("Abcdefg1")
        assertEquals(4, rules.size)
        assertEquals(3, rules.count { it.required })
        val symbolRule = rules.first { !it.required }
        assertFalse(symbolRule.isMet)
    }
}
