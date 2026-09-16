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
        listOf("message-task", "job-assigned", "jobs-all-completed", "shift-started", null).forEach { event ->
            assertEquals(event.toString(), "home", FcmEventRouting.deepLink(event, null))
        }
    }

    @Test
    fun `payslip-generated routes to Account, not Home — Payslips is pushed from there`() {
        assertEquals("account", FcmEventRouting.deepLink("payslip-generated", null))
    }

    @Test
    fun `channel routing matches each event's purpose`() {
        assertEquals(NotificationChannels.SHIFT_UPCOMING, FcmEventRouting.channelId("roster-published"))
        assertEquals(NotificationChannels.SHIFT_UPCOMING, FcmEventRouting.channelId("shift-cancelled"))
        assertEquals(NotificationChannels.SHIFT_UPCOMING, FcmEventRouting.channelId("shift-start-6h"))
        assertEquals(NotificationChannels.SHIFT_UPCOMING, FcmEventRouting.channelId("shift-start-30m"))
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

    @Test
    fun `notificationId is scoped per shift for events with a local-alert counterpart`() {
        // RosterMessagingService (push) and LocalAlertObserver (live-listener backup) both call this
        // same function for roster-published/timesheet-approved/timesheet-rejected — sharing one
        // formula is what makes the OS collapse a duplicate push+local pair into one notification.
        listOf("roster-published", "timesheet-approved", "timesheet-rejected").forEach { event ->
            val forShiftOne = FcmEventRouting.notificationId(event, "shift-1")
            val forShiftTwo = FcmEventRouting.notificationId(event, "shift-2")
            assertEquals(event, false, forShiftOne == forShiftTwo)
        }
    }

    @Test
    fun `notificationId falls back to the event name when shiftId is absent`() {
        assertEquals(
            "timesheet-rejected".hashCode(),
            FcmEventRouting.notificationId("timesheet-rejected", null),
        )
    }

    @Test
    fun `notificationId for events with no local-alert counterpart ignores shiftId`() {
        // job-assigned etc. only ever arrive via push — no need to scope per shift.
        val withShift = FcmEventRouting.notificationId("job-assigned", "shift-1")
        val withoutShift = FcmEventRouting.notificationId("job-assigned", null)
        assertEquals(withoutShift, withShift)
    }

    @Test
    fun `notificationId never crashes on a null event`() {
        assertEquals("rosterra".hashCode(), FcmEventRouting.notificationId(null, null))
    }
}
