package com.surainvestments.roster.domain.model

import com.surainvestments.roster.notifications.NotificationChannels
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/**
 * One concrete, ready-to-arm local notification: a specific instant plus the exact copy and
 * routing to show when it fires. Serializable so the armed set can be persisted and re-armed
 * after a device reboot without re-reading Firestore (mirrors iOS's "pre-schedule at sync time,
 * no background fetch" model — `ANDROID-STAFF-BUILD-PLAN.md` §6).
 */
@Serializable
data class ScheduledReminder(
    val shiftId: String,
    val slotTag: String,
    val fireAtEpochMs: Long,
    val title: String,
    val body: String,
    val channelId: String,
    val deepLink: String,
) {
    val fireAt: Instant get() = Instant.ofEpochMilli(fireAtEpochMs)

    /** Stable per (shift, slot) — used as both the AlarmManager requestCode and the notification id, so a rebuild replaces rather than duplicates. */
    val requestCode: Int get() = "$shiftId.$slotTag".hashCode()
}

/**
 * Pure computation of a staff member's local shift reminders — the Android port of iOS
 * `ShiftReminderScheduler.sync` (`IOS-STAFF-AUDIT.md` §10). No Android/Firestore/AlarmManager
 * dependency, so the exact slot table and gating can be unit-tested bit-for-bit.
 *
 * All slots are relative to the shift's **rostered** start/end (not any verified clock time) and
 * are only emitted when their fire instant is still in the future — an alarm can't be set for the
 * past, so a moment that has already passed is simply dropped. Rebuilt idempotently on every
 * shifts/timesheets/clock-session change by the caller; capped at [MAX_SHIFTS].
 */
object ShiftReminderPlanner {

    /** iOS caps at 8 shifts (its 64-pending-notification ceiling ÷ 8 slots); Android has no such OS limit but caps anyway for battery/Doze sanity. */
    const val MAX_SHIFTS = 8

    /** A filed timesheet suppresses the "start"/"submit" nudges — the staff member has already acted. */
    private val FILED = setOf(TimesheetStatus.Pending, TimesheetStatus.Approved, TimesheetStatus.AbsentReported, TimesheetStatus.Absent)

    /** A reported/confirmed absence suppresses every reminder for that shift — there's nothing to attend. */
    private val ABSENT = setOf(TimesheetStatus.AbsentReported, TimesheetStatus.Absent)

    fun plan(
        shifts: List<Shift>,
        timesheetsByShiftId: Map<String, Timesheet>,
        clockedInShiftId: String?,
        now: Instant = Instant.now(),
        maxShifts: Int = MAX_SHIFTS,
    ): List<ScheduledReminder> =
        shifts
            .filter { it.status == ShiftStatus.Published }
            .sortedBy { it.startDateTime }
            .mapNotNull { shift ->
                remindersFor(shift, timesheetsByShiftId[shift.id]?.status, clockedInShiftId, now)
                    .takeIf { it.isNotEmpty() }
            }
            .take(maxShifts)
            .flatten()

    private fun remindersFor(
        shift: Shift,
        status: TimesheetStatus?,
        clockedInShiftId: String?,
        now: Instant,
    ): List<ScheduledReminder> {
        val filed = status in FILED
        val absent = status in ABSENT
        val start = shift.startDateTime
        val end = shift.endDateTime
        val startTime = RosterFormat.timeOfDay(shift.rosteredStart)
        val out = mutableListOf<ScheduledReminder>()

        fun add(slotTag: String, fireAt: Instant, title: String, body: String, channelId: String, deepLink: String) {
            if (fireAt.isAfter(now)) {
                out += ScheduledReminder(shift.id, slotTag, fireAt.toEpochMilli(), title, body, channelId, deepLink)
            }
        }

        // Pre-shift countdown — suppressed only if the staff member reported/was marked absent.
        // Deliberately NO local "6h"/"30m" slots: the Worker's own cron (`shift-start-6h`/
        // `shift-start-30m`, worker/cron/shiftStart.ts) independently pushes at those exact same
        // two instants to every platform with a registered FCM token, Android included — a local
        // slot at the same offset would show as a second, separately-worded banner a second or
        // two apart. This is a confirmed, currently-live duplicate on iOS (still open — see
        // `docs/NOTIFICATION-SYSTEM-AUDIT-REPORT.md` §5.3/§1.4) that Android would otherwise
        // reproduce; the fix here matches that report's own recommendation (suppress the local
        // slot, rely on the shared server cron). 24h/1h/5m have no server-cron counterpart at all
        // and stay local-only, same as forgot-start/forgot-end/submit-hours below.
        if (!absent) {
            add("24h", start.minus(24, ChronoUnit.HOURS), "Shift tomorrow", "You have a shift tomorrow at $startTime.", NotificationChannels.SHIFT_UPCOMING, "home")
            add("1h", start.minus(1, ChronoUnit.HOURS), "Shift soon", "Your shift starts in 1 hour, at $startTime.", NotificationChannels.SHIFT_UPCOMING, "home")
            add("5m", start.minus(5, ChronoUnit.MINUTES), "Ready to start?", "Start Shift is now available.", NotificationChannels.SHIFT_UPCOMING, "home")
        }

        // "Forgot to start" — only while the shift is still unactioned and not yet clocked in.
        // Clocking in flips clockedInShiftId, so the next rebuild drops (and cancels) this slot.
        if (!absent && !filed && clockedInShiftId != shift.id) {
            add("forgot-start", start.plus(10, ChronoUnit.MINUTES), "Don't forget to start your shift", "Your shift has started — tap to clock in.", NotificationChannels.SHIFT_UPCOMING, "home")
        }

        // "Forgot to clock out" — armed only while actively clocked in on this shift.
        if (clockedInShiftId == shift.id) {
            add("forgot-end", end.plus(10, ChronoUnit.MINUTES), "Your shift has ended", "Don't forget to clock out.", NotificationChannels.TIMESHEET_ACTION, "submit:${shift.id}")
        }

        // "Submit your hours" — only if no filed timesheet exists yet.
        if (!absent && !filed) {
            add("submit-hours", end.plus(15, ChronoUnit.MINUTES), "Submit your hours", "Your shift has ended — submit your worked hours.", NotificationChannels.TIMESHEET_ACTION, "submit:${shift.id}")
        }

        return out
    }
}
