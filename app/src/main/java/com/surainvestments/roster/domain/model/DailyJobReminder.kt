package com.surainvestments.roster.domain.model

import com.surainvestments.roster.notifications.NotificationChannels
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Pure computation of "please check your daily jobs" local reminders — the Android port of iOS
 * `DailyJobReminderScheduler.sync`. No Android/Firestore/AlarmManager dependency, so the cadence
 * and gating can be unit-tested bit-for-bit, same as [ShiftReminderPlanner].
 *
 * One reminder per hour of *today's* shift, starting an hour after it begins and stopping
 * [QUIET_PERIOD_BEFORE_END] before it ends — only for a shift that still has at least one
 * incomplete daily job. Deliberately generic copy, never naming a specific job. Rebuilt
 * idempotently by the caller on every shifts/assignments change, so completing the last job (or
 * a manager's repeat-daily rule auto-assigning jobs to a new shift) is reflected on the next plan.
 *
 * [windowAssignments] is deliberately NOT pre-filtered to today by the caller — "today" is
 * re-derived here from [now] on every call instead, so a long-lived caller subscribed to a wide
 * date-range query (weeks, not a single day) never goes stale at midnight the way a query scoped
 * to a single day at subscribe time would.
 */
object DailyJobReminderPlanner {

    /** Mirrors iOS's cap — only today's shifts are relevant, so this stays small regardless. */
    const val MAX_SHIFTS = 4
    private val CHECK_IN_INTERVAL = ChronoUnit.HOURS.duration
    private val QUIET_PERIOD_BEFORE_END = java.time.Duration.ofMinutes(15)

    fun plan(
        shifts: List<Shift>,
        windowAssignments: List<DailyJobAssignment>,
        now: Instant = Instant.now(),
        maxShifts: Int = MAX_SHIFTS,
    ): List<ScheduledReminder> {
        val todayKey = RosterCalendar.todayKey(now)
        val incompleteByShift = windowAssignments
            .filter { it.date == todayKey && !it.completed }
            .groupBy { it.shiftId }
            .keys

        return shifts
            .filter { it.status == ShiftStatus.Published && it.date == todayKey }
            .filter { it.id in incompleteByShift }
            .filter { it.endDateTime.isAfter(now) }
            .sortedBy { it.startDateTime }
            .take(maxShifts)
            .flatMap { remindersFor(it, now) }
    }

    private fun remindersFor(shift: Shift, now: Instant): List<ScheduledReminder> {
        val latest = shift.endDateTime.minus(QUIET_PERIOD_BEFORE_END)
        val out = mutableListOf<ScheduledReminder>()
        var offset = CHECK_IN_INTERVAL
        var index = 0
        while (true) {
            val fireAt = shift.startDateTime.plus(offset)
            if (fireAt.isAfter(latest)) break
            if (fireAt.isAfter(now)) {
                out += ScheduledReminder(
                    shiftId = shift.id,
                    slotTag = "daily-jobs.$index",
                    fireAtEpochMs = fireAt.toEpochMilli(),
                    title = "Daily Jobs",
                    body = "Please check your daily jobs.",
                    channelId = NotificationChannels.TASKS,
                    deepLink = "home",
                )
            }
            offset = offset.plus(CHECK_IN_INTERVAL)
            index += 1
        }
        return out
    }
}
