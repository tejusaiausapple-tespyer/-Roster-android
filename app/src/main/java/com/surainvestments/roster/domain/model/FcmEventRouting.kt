package com.surainvestments.roster.domain.model

import com.surainvestments.roster.notifications.NotificationChannels

/**
 * Maps an incoming FCM `event` (the shared registry also used by the Worker/PWA/iOS — see
 * `IOS-STAFF-AUDIT.md` §10) to a deep-link string consumed the same way as a local reminder's
 * (`ANDROID-STAFF-BUILD-PLAN.md` §5 deep-link table) and to the notification channel it belongs
 * in. Pure/no Android dependency beyond the channel-id constants, so the routing table itself is
 * unit-testable.
 *
 * `message-task` is deliberately unhandled (falls through every `when` to its generic default) —
 * Messages is out of scope for Android entirely (product decision, 2026-07-24, see
 * `ANDROID-BUILD-PLAN.md` §1.4 item 7). The Worker may still send this event if a PWA manager
 * sends a message, since that feature remains live there; Android just has no inbox to show it
 * in, so it degrades to a generic "Rosterra" notification landing on Home rather than crashing
 * or silently dropping the push.
 */
object FcmEventRouting {

    /** `timesheet-rejected` needs the shiftId to land on Submit Hours for that specific shift; every other routed event just needs the tab. */
    fun deepLink(event: String?, shiftId: String?): String = when (event) {
        "timesheet-rejected" -> shiftId?.let { "submit:$it" } ?: "roster"
        "timesheet-approved", "roster-published", "shift-changed", "shift-cancelled" -> "roster"
        else -> "home"
    }

    fun channelId(event: String?): String = when (event) {
        "roster-published", "shift-changed", "shift-cancelled", "shift-started", "shift-ended" -> NotificationChannels.SHIFT_UPCOMING
        "timesheet-approved", "timesheet-rejected", "timesheet-reminder" -> NotificationChannels.TIMESHEET_ACTION
        "job-assigned", "jobs-all-completed" -> NotificationChannels.TASKS
        else -> NotificationChannels.GENERAL
    }

    fun defaultTitle(event: String?): String = when (event) {
        "roster-published" -> "Roster published"
        "timesheet-approved" -> "Hours approved"
        "timesheet-rejected" -> "Hours rejected"
        "timesheet-reminder" -> "Submit your hours"
        "shift-changed" -> "Shift updated"
        "shift-cancelled" -> "Shift cancelled"
        "job-assigned" -> "New job assigned"
        "jobs-all-completed" -> "All jobs completed"
        "payslip-generated" -> "Payslip ready"
        else -> "Rosterra"
    }
}
