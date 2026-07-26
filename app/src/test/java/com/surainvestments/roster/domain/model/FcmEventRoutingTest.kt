package com.surainvestments.roster.domain.model

import com.surainvestments.roster.notifications.NotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Test

/** The FCM event → deep-link/channel table must match `ANDROID-STAFF-BUILD-PLAN.md` §5 exactly. */
class FcmEventRoutingTest {

    @Test
    fun `timesheet-rejected routes to Submit Hours for the specific shift`() {
        assertEquals("submit:s1", FcmEventRouting.deepLink("timesheet-rejected", "s1"))
    }

    @Test
    fun `timesheet-rejected without a shiftId falls back to Roster`() {
        assertEquals("roster", FcmEventRouting.deepLink("timesheet-rejected", null))
    }

    @Test
    fun `roster-affecting events land on Roster`() {
        listOf("timesheet-approved", "roster-published", "shift-changed", "shift-cancelled").forEach { event ->
            assertEquals(event, "roster", FcmEventRouting.deepLink(event, null))
        }
    }

    @Test
    fun `unrecognized or staff-inapplicable events fall back to Home`() {
        listOf("message-task", "job-assigned", "jobs-all-completed", "payslip-generated", "shift-started", null).forEach { event ->
            assertEquals(event.toString(), "home", FcmEventRouting.deepLink(event, null))
        }
    }

    @Test
    fun `channel routing matches each event's purpose`() {
        assertEquals(NotificationChannels.SHIFT_UPCOMING, FcmEventRouting.channelId("roster-published"))
        assertEquals(NotificationChannels.SHIFT_UPCOMING, FcmEventRouting.channelId("shift-cancelled"))
        assertEquals(NotificationChannels.TIMESHEET_ACTION, FcmEventRouting.channelId("timesheet-approved"))
        assertEquals(NotificationChannels.TIMESHEET_ACTION, FcmEventRouting.channelId("timesheet-rejected"))
        assertEquals(NotificationChannels.TASKS, FcmEventRouting.channelId("job-assigned"))
        assertEquals(NotificationChannels.GENERAL, FcmEventRouting.channelId("message-task"))
        assertEquals(NotificationChannels.GENERAL, FcmEventRouting.channelId(null))
    }

    @Test
    fun `every named event has a non-generic default title`() {
        val named = listOf(
            "roster-published", "timesheet-approved", "timesheet-rejected", "timesheet-reminder",
            "shift-changed", "shift-cancelled", "job-assigned", "jobs-all-completed", "payslip-generated",
        )
        named.forEach { event -> assertEquals(event, false, FcmEventRouting.defaultTitle(event) == "Rosterra") }
        assertEquals("Rosterra", FcmEventRouting.defaultTitle(null))
    }

    @Test
    fun `message-task is deliberately unhandled — Messages is out of scope for Android`() {
        // Still degrades gracefully (Home, generic title, general channel) rather than crashing,
        // since the Worker may still send this event when a PWA manager sends a message.
        assertEquals("home", FcmEventRouting.deepLink("message-task", null))
        assertEquals(NotificationChannels.GENERAL, FcmEventRouting.channelId("message-task"))
        assertEquals("Rosterra", FcmEventRouting.defaultTitle("message-task"))
    }
}
