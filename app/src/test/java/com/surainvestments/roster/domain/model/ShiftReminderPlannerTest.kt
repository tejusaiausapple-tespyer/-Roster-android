package com.surainvestments.roster.domain.model

import com.surainvestments.roster.notifications.NotificationChannels
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the local shift-reminder slot table + gating (`ShiftReminderPlanner`), the Android port of iOS's ShiftReminderScheduler. */
class ShiftReminderPlannerTest {

    // Fixed "now": Monday 1 June 2026, 08:00 Adelaide.
    private val now = LocalDate.parse("2026-06-01").atTime(LocalTime.of(8, 0)).atZone(RosterCalendar.zoneId).toInstant()

    private fun shift(
        id: String,
        date: String,
        start: String,
        end: String,
        status: ShiftStatus = ShiftStatus.Published,
        location: String? = "Main St",
    ) = Shift(
        id = id,
        staffId = "staff-1",
        date = date,
        rosteredStart = start,
        rosteredEnd = end,
        breakMinutes = 0,
        scheduledHours = 0.0,
        location = location,
        department = null,
        notes = null,
        status = status,
        submittableAfter = null,
        shiftStartAt = null,
    )

    private fun timesheet(shiftId: String, status: TimesheetStatus) = Timesheet(
        id = shiftId, shiftId = shiftId, staffId = "staff-1",
        actualStart = "", actualEnd = "", actualBreakMinutes = 0, workedHours = 0.0,
        staffNotes = null, status = status, managerNotes = null, approvedBy = null,
        approvedAt = null, rejectedReason = null, submittedAt = null, updatedAt = null,
    )

    private fun tags(reminders: List<ScheduledReminder>) = reminders.map { it.slotTag }.toSet()

    @Test
    fun `future shift with no timesheet emits every future pre-start slot plus forgot-start and submit-hours`() {
        // Shift two days out (17:00–21:00) so all three local pre-start instants are still in the future.
        // No local "6h"/"30m" — those are deliberately left to the Worker's own cron push to
        // avoid a duplicate banner (see the doc comment on ShiftReminderPlanner.remindersFor).
        val shifts = listOf(shift("s1", "2026-06-03", "17:00", "21:00"))

        val reminders = ShiftReminderPlanner.plan(shifts, emptyMap(), clockedInShiftId = null, now = now)

        // No forgot-end (not clocked in). Every other slot present.
        assertEquals(
            setOf("24h", "1h", "5m", "forgot-start", "submit-hours"),
            tags(reminders),
        )
        assertFalse("forgot-end only arms while clocked in", "forgot-end" in tags(reminders))
        assertFalse("6h is left to the server cron push, not scheduled locally", "6h" in tags(reminders))
        assertFalse("30m is left to the server cron push, not scheduled locally", "30m" in tags(reminders))
    }

    @Test
    fun `24h slot is dropped once the shift is under 24h away`() {
        // Shift today at 17:00 is only ~9h out, so the 24h-before instant is already in the past.
        val reminders = ShiftReminderPlanner.plan(listOf(shift("s1", "2026-06-01", "17:00", "21:00")), emptyMap(), null, now)
        assertFalse("24h-before is in the past", "24h" in tags(reminders))
        // A shift two days out keeps its 24h slot.
        val far = ShiftReminderPlanner.plan(listOf(shift("s2", "2026-06-03", "17:00", "21:00")), emptyMap(), null, now)
        assertTrue("24h" in tags(far))
    }

    @Test
    fun `pre-start slot fire instants are exactly offset from rostered start`() {
        val shifts = listOf(shift("s1", "2026-06-03", "17:00", "21:00")) // two days out — all slots future
        val reminders = ShiftReminderPlanner.plan(shifts, emptyMap(), null, now)
        val start = shifts.first().startDateTime
        val byTag = reminders.associate { it.slotTag to it.fireAt }

        assertEquals(start.minusSeconds(24 * 3600), byTag["24h"])
        assertEquals(start.minusSeconds(3600), byTag["1h"])
        assertEquals(start.minusSeconds(5 * 60), byTag["5m"])
    }

    @Test
    fun `clocking in cancels forgot-start and arms forgot-end`() {
        val shifts = listOf(shift("s1", "2026-06-01", "17:00", "21:00"))
        val reminders = ShiftReminderPlanner.plan(shifts, emptyMap(), clockedInShiftId = "s1", now = now)

        assertFalse("forgot-start must vanish once clocked in", "forgot-start" in tags(reminders))
        assertTrue("forgot-end arms while clocked in", "forgot-end" in tags(reminders))
        // forgot-end fires 10 min after rostered end.
        assertEquals(shifts.first().endDateTime.plusSeconds(10 * 60), reminders.first { it.slotTag == "forgot-end" }.fireAt)
    }

    @Test
    fun `a filed timesheet suppresses forgot-start and submit-hours but not the pre-start countdown`() {
        val shifts = listOf(shift("s1", "2026-06-03", "17:00", "21:00"))
        val reminders = ShiftReminderPlanner.plan(shifts, mapOf("s1" to timesheet("s1", TimesheetStatus.Pending)), null, now)

        assertTrue("pre-start reminders still fire for a submitted shift", "5m" in tags(reminders))
        assertFalse("forgot-start" in tags(reminders))
        assertFalse("submit-hours" in tags(reminders))
    }

    @Test
    fun `a reported absence suppresses every reminder for that shift`() {
        val shifts = listOf(shift("s1", "2026-06-03", "17:00", "21:00"))
        val reminders = ShiftReminderPlanner.plan(shifts, mapOf("s1" to timesheet("s1", TimesheetStatus.AbsentReported)), null, now)
        assertTrue("no reminders once absence is reported", reminders.isEmpty())
    }

    @Test
    fun `rejected timesheet still needs reminders (not a filed status)`() {
        val shifts = listOf(shift("s1", "2026-06-03", "17:00", "21:00"))
        val reminders = ShiftReminderPlanner.plan(shifts, mapOf("s1" to timesheet("s1", TimesheetStatus.Rejected)), null, now)
        assertTrue("rejected → still nudge to resubmit", "submit-hours" in tags(reminders))
        assertTrue("forgot-start" in tags(reminders))
    }

    @Test
    fun `draft shifts are ignored`() {
        val shifts = listOf(shift("s1", "2026-06-03", "17:00", "21:00", status = ShiftStatus.Draft))
        assertTrue(ShiftReminderPlanner.plan(shifts, emptyMap(), null, now).isEmpty())
    }

    @Test
    fun `only the nearest maxShifts shifts are scheduled`() {
        // 10 future shifts, one per day; cap at 8 (by soonest start).
        val shifts = (1..10).map { d -> shift("s$d", "2026-06-%02d".format(d + 1), "17:00", "21:00") }
        val reminders = ShiftReminderPlanner.plan(shifts, emptyMap(), null, now, maxShifts = 8)

        val scheduledShiftIds = reminders.map { it.shiftId }.toSet()
        assertEquals(8, scheduledShiftIds.size)
        // The two furthest-out shifts (s9, s10) are dropped.
        assertFalse("s9" in scheduledShiftIds)
        assertFalse("s10" in scheduledShiftIds)
        assertTrue("s1" in scheduledShiftIds)
    }


    @Test
    fun `channel + deep link routing matches the slot's purpose`() {
        val reminders = ShiftReminderPlanner.plan(listOf(shift("s1", "2026-06-03", "17:00", "21:00")), emptyMap(), null, now)
        val byTag = reminders.associateBy { it.slotTag }

        assertEquals(NotificationChannels.SHIFT_UPCOMING, byTag.getValue("5m").channelId)
        assertEquals("home", byTag.getValue("5m").deepLink)
        assertEquals(NotificationChannels.TIMESHEET_ACTION, byTag.getValue("submit-hours").channelId)
        assertEquals("submit:s1", byTag.getValue("submit-hours").deepLink)
    }
}
