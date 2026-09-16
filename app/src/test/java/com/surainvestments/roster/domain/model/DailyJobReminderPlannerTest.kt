package com.surainvestments.roster.domain.model

import com.surainvestments.roster.notifications.NotificationChannels
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the local "check your daily jobs" reminder cadence + gating (`DailyJobReminderPlanner`), the Android port of iOS's DailyJobReminderScheduler. */
class DailyJobReminderPlannerTest {

    // Fixed "now": Monday 1 June 2026, 08:00 Adelaide — before the test shift's 09:00 start.
    private val now = LocalDate.parse("2026-06-01").atTime(LocalTime.of(8, 0)).atZone(RosterCalendar.zoneId).toInstant()
    private val today = "2026-06-01"
    private val tomorrow = "2026-06-02"

    private fun shift(
        id: String,
        date: String = today,
        start: String = "09:00",
        end: String = "17:00",
        status: ShiftStatus = ShiftStatus.Published,
    ) = Shift(
        id = id, staffId = "staff-1", date = date, rosteredStart = start, rosteredEnd = end,
        breakMinutes = 0, scheduledHours = 0.0, location = "Main St", department = null, notes = null,
        status = status, submittableAfter = null, shiftStartAt = null,
    )

    private fun assignment(shiftId: String, id: String = "$shiftId-a1", date: String = today, completed: Boolean = false) =
        DailyJobAssignment(
            id = id, shiftId = shiftId, staffId = "staff-1", templateId = "t1", title = "Wipe counters",
            date = date, assignedAt = null, assignedBy = null, completed = completed, completedAt = null, completedBy = null,
        )

    private fun tags(reminders: List<ScheduledReminder>) = reminders.map { it.slotTag }.toSet()

    @Test
    fun `today's shift with an incomplete job gets hourly reminders from +1h to 15m before end`() {
        val reminders = DailyJobReminderPlanner.plan(
            shifts = listOf(shift("s1")),
            windowAssignments = listOf(assignment("s1")),
            now = now,
        )
        // 09:00-17:00 shift: +1h..+7h (16:00) are <= end-15m (16:45); +8h (17:00) is not.
        assertEquals(7, reminders.size)
        assertEquals(setOf("daily-jobs.0", "daily-jobs.6"), tags(reminders).let { setOf(it.min(), it.max()) })
    }

    @Test
    fun `a shift with every job already completed gets no reminders`() {
        val reminders = DailyJobReminderPlanner.plan(
            shifts = listOf(shift("s1")),
            windowAssignments = listOf(assignment("s1", completed = true)),
            now = now,
        )
        assertTrue("staff already finished — no nagging", reminders.isEmpty())
    }

    @Test
    fun `a shift with no daily job assignments at all gets no reminders`() {
        val reminders = DailyJobReminderPlanner.plan(
            shifts = listOf(shift("s1")),
            windowAssignments = emptyList(),
            now = now,
        )
        assertTrue(reminders.isEmpty())
    }

    @Test
    fun `only today's shift is considered, not a future one`() {
        val reminders = DailyJobReminderPlanner.plan(
            shifts = listOf(shift("s1", date = tomorrow)),
            windowAssignments = listOf(assignment("s1", date = tomorrow)),
            now = now,
        )
        assertTrue("daily-jobs reminders only ever cover today's shift", reminders.isEmpty())
    }

    @Test
    fun `a draft shift is ignored even with an incomplete job`() {
        val reminders = DailyJobReminderPlanner.plan(
            shifts = listOf(shift("s1", status = ShiftStatus.Draft)),
            windowAssignments = listOf(assignment("s1")),
            now = now,
        )
        assertTrue(reminders.isEmpty())
    }

    @Test
    fun `fire instants are exactly hourly from rostered start`() {
        val shifts = listOf(shift("s1"))
        val reminders = DailyJobReminderPlanner.plan(shifts, listOf(assignment("s1")), now)
        val start = shifts.first().startDateTime
        val byTag = reminders.associate { it.slotTag to it.fireAt }

        assertEquals(start.plusSeconds(3600), byTag["daily-jobs.0"])
        assertEquals(start.plusSeconds(2 * 3600), byTag["daily-jobs.1"])
    }

    @Test
    fun `only the nearest maxShifts shifts are scheduled`() {
        val shifts = (1..6).map { d -> shift("s$d") }
        val assignments = (1..6).map { d -> assignment("s$d") }
        val reminders = DailyJobReminderPlanner.plan(shifts, assignments, now, maxShifts = 2)

        assertEquals(2, reminders.map { it.shiftId }.toSet().size)
    }

    @Test
    fun `content is generic, never naming a specific job`() {
        val reminders = DailyJobReminderPlanner.plan(listOf(shift("s1")), listOf(assignment("s1")), now)
        val first = reminders.first()

        assertEquals("Daily Jobs", first.title)
        assertEquals("Please check your daily jobs.", first.body)
        assertFalse("Wipe counters" in first.body)
        assertEquals(NotificationChannels.TASKS, first.channelId)
        assertEquals("home", first.deepLink)
    }
}
