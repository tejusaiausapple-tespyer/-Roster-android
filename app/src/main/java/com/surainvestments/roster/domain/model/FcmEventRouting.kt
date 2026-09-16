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

    /**
     * `timesheet-rejected` needs the shiftId to land on Submit Hours for that specific shift;
     * `payslip-generated` lands on Account (Payslips is pushed from there, not a tab — a
     * generic `"home"` fallback would be the wrong destination, the same class of bug as iOS's
     * still-open payslip-notification routing issue, `docs/NOTIFICATION-SYSTEM-AUDIT-REPORT.md`
     * §5.7); every other routed event just needs the tab.
     */
    fun deepLink(event: String?, shiftId: String?): String = when (event) {
        "timesheet-rejected" -> shiftId?.let { "submit:$it" } ?: "roster"
        "timesheet-approved", "roster-published", "shift-changed", "shift-cancelled" -> "roster"
        "payslip-generated" -> "account"
        else -> "home"
    }

    fun channelId(event: String?): String = when (event) {
        "roster-published", "shift-changed", "shift-cancelled", "shift-started", "shift-ended",
        "shift-start-6h", "shift-start-30m",
        -> NotificationChannels.SHIFT_UPCOMING
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

    /**
     * Canonical Android notification ID for an (event, shiftId) pair. `LocalAlertObserver` (a live
     * Firestore-listener backup) and `RosterMessagingService` (the FCM push) both fire for the same
     * underlying `roster-published`/`timesheet-approved`/`timesheet-rejected` events — they must
     * produce the *same* ID for the same event+shift so `NotificationManager`'s same-ID-replaces
     * behavior collapses them into one notification instead of showing both. Every other event only
     * ever arrives via push, so it's keyed on the event name alone.
     */
    fun notificationId(event: String?, shiftId: String?): Int {
        val key = if (shiftId != null && hasLocalAlertCounterpart(event)) "$event-$shiftId" else event
        return (key ?: "rosterra").hashCode()
    }

    private fun hasLocalAlertCounterpart(event: String?): Boolean = when (event) {
        "roster-published", "timesheet-approved", "timesheet-rejected" -> true
        else -> false
    }
}
