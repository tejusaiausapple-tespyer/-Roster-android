package com.surainvestments.roster.domain.model

import java.time.Instant

/**
 * Mirrors iOS `BusinessRules` (`Models/BusinessRules.swift`) — shared decision logic. Staff-only
 * scope: `payrollGaps` (manager-only, drives the manager Payroll Gaps sheet) is deliberately not
 * ported here — see `ANDROID-STAFF-BUILD-PLAN.md`.
 */
object BusinessRules {

    const val breakMinutesMin = 0
    const val breakMinutesMax = 90
    const val breakMinutesStep = 5

    /** Staff shift listener window: 28 days back, 56 days forward. */
    const val shiftWindowDaysBack = 28
    const val shiftWindowDaysForward = 56

    /** Oldest timesheet a staff member's own history query includes (5 years). */
    const val staffTimesheetCutoffDays = 365 * 5

    /**
     * Oldest timesheet the manager's all-staff listener loads live (recent operational window,
     * not staff-facing — named here only because [TimesheetRepository.recentTimesheets] already
     * used this magic number and deserved a name, not because this phase builds manager UI).
     */
    const val managerTimesheetWindowDaysBack = 90

    /** Availability may be set up to 12 weeks ahead; navigation allows 2 weeks back (locked). */
    const val availabilityMinWeekOffset = -2
    const val availabilityMaxWeekOffset = 12

    /** AU Superannuation Guarantee fallback when a staff member has no override set. */
    const val defaultSuperRatePercent = 12.0

    /** Staff may start their shift this many seconds before the rostered start ("early check-in"). */
    const val earlyClockInWindowSeconds = 5 * 60

    /** Lenient allowance (metres) for starting a shift when the location's geofence isn't enforced. */
    const val lenientStartRadiusMetres = 250.0

    /** Rounds to the nearest [breakMinutesStep] and clamps to [breakMinutesMin]..[breakMinutesMax]. */
    fun clampBreakMinutes(minutes: Int): Int = minutes.coerceIn(breakMinutesMin, breakMinutesMax)

    /**
     * Worked hours from "HH:mm" start/end + a break, rounded to 2 decimals. Crosses midnight if
     * [end] is earlier than [start]. Mirrors iOS `calcWorkedHours`.
     */
    fun calcWorkedHours(start: String, end: String, breakMinutes: Int): Double {
        val s = start.split(":").mapNotNull { it.toIntOrNull() }
        val e = end.split(":").mapNotNull { it.toIntOrNull() }
        if (s.size < 2 || e.size < 2) return 0.0
        val startMins = s[0] * 60 + s[1]
        var endMins = e[0] * 60 + e[1]
        if (endMins < startMins) endMins += 24 * 60
        val totalMins = endMins - startMins - breakMinutes
        val hours = maxOf(0, totalMins) / 60.0
        return Math.round(hours * 100.0) / 100.0
    }

    /**
     * The staff-facing display status for a shift — distinct from [ManagerShiftStatus] (which
     * collapses `absent_reported`/`absent` into one bucket and never shows `draft`/`pending`
     * verbatim). Mirrors iOS `displayStatus`.
     */
    fun displayStatus(shift: Shift, timesheet: Timesheet?, now: Instant = Instant.now()): StaffShiftDisplayStatus {
        if (timesheet != null) {
            return StaffShiftDisplayStatus.fromRaw(timesheet.status.rawValue) ?: StaffShiftDisplayStatus.Pending
        }
        return if (shift.isSubmittable(now)) StaffShiftDisplayStatus.AwaitingSubmission else StaffShiftDisplayStatus.Scheduled
    }

    /** Whether a shift currently needs staff action (submit / resubmit / undo). */
    fun needsStaffAction(shift: Shift, timesheet: Timesheet?, now: Instant = Instant.now()): Boolean {
        if (timesheet == null && shift.isSubmittable(now)) return true
        if (timesheet?.status == TimesheetStatus.Rejected) return true
        if (timesheet != null && timesheet.isStaffReportedAbsence) return true
        return false
    }

    /** Whether staff may report an absence for this shift (no timesheet yet, or a rejected one). */
    fun canReportAbsence(shift: Shift, timesheet: Timesheet?, now: Instant = Instant.now()): Boolean {
        if (!shift.isSubmittable(now)) return false
        return timesheet?.let { it.status == TimesheetStatus.Rejected } ?: true
    }

    /**
     * Whether staff may submit/edit/resubmit hours for this shift — matches the Firestore
     * rules exactly (pending/rejected/draft stay staff-editable; approved/absent don't).
     */
    fun canSubmitHours(shift: Shift, timesheet: Timesheet?, now: Instant = Instant.now()): Boolean {
        if (shift.status != ShiftStatus.Published || !shift.isSubmittable(now)) return false
        val status = timesheet?.status ?: return true
        return status == TimesheetStatus.Rejected || status == TimesheetStatus.Pending || status == TimesheetStatus.Draft
    }

    /**
     * How far the Roster tab's week selector may navigate, in whole weeks relative to the
     * current week — derived from the same ±28/56-day shift listener window so the selector
     * never lets staff scroll to a week with no data. Mirrors iOS `shiftWeekOffsetBounds`.
     */
    fun shiftWeekOffsetBounds(now: Instant = Instant.now()): IntRange {
        val todayMonday = RosterCalendar.parseDateKey(RosterCalendar.weekStartKey(now)) ?: return -4..8
        val startMonday = RosterCalendar.parseDateKey(
            RosterCalendar.weekStartKey(now.minus(shiftWindowDaysBack.toLong(), java.time.temporal.ChronoUnit.DAYS)),
        ) ?: return -4..8
        val endMonday = RosterCalendar.parseDateKey(
            RosterCalendar.weekStartKey(now.plus(shiftWindowDaysForward.toLong(), java.time.temporal.ChronoUnit.DAYS)),
        ) ?: return -4..8
        val minWeeks = java.time.temporal.ChronoUnit.WEEKS.between(todayMonday, startMonday).toInt()
        val maxWeeks = java.time.temporal.ChronoUnit.WEEKS.between(todayMonday, endMonday).toInt()
        return minWeeks..maxWeeks
    }

    /** Current week and all past weeks are always locked for staff availability edits. */
    fun isWeekLockedForStaff(weekStartKey: String, now: Instant = Instant.now()): Boolean =
        weekStartKey <= RosterCalendar.weekStartKey(now)

    /** Full lock decision: past/current always locked, future locked only if a manager locked that week. */
    fun isWeekLockedForStaff(weekStartKey: String, managerLockedWeeks: Set<String>, now: Instant = Instant.now()): Boolean =
        isWeekLockedForStaff(weekStartKey, now) || managerLockedWeeks.contains(weekStartKey)

    /** Every Monday key from [fromMondayKey] through the availability horizon ([availabilityMaxWeekOffset] weeks out). */
    fun recurringWeekKeys(fromMondayKey: String, now: Instant = Instant.now()): List<String> {
        val horizonMonday = RosterCalendar.parseDateKey(RosterCalendar.weekStartKey(now))
            ?.plusWeeks(availabilityMaxWeekOffset.toLong()) ?: return emptyList()
        var monday = RosterCalendar.parseDateKey(fromMondayKey) ?: return emptyList()
        val keys = mutableListOf<String>()
        while (!monday.isAfter(horizonMonday)) {
            keys += monday.toString()
            monday = monday.plusWeeks(1)
        }
        return keys
    }

    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun isValidEmail(email: String): Boolean = emailPattern.matches(email.trim())

    /**
     * Derives a shift's manager-facing lifecycle status. Exact precedence, first match wins:
     * 1. A submitted [Timesheet] decides it outright (approved/pending→awaitingApproval/
     *    rejected/absence), *except* `draft`, which falls through as if no timesheet existed.
     * 2. Otherwise, verified [ShiftAttendance] takes over: an early clock-out ends "in
     *    progress" immediately (pendingSubmission) regardless of the clock; a clock-in with
     *    no clock-out yet, before the shift's scheduled end, is inProgress.
     * 3. Otherwise, fall back to the plain schedule clock (scheduled / inProgress / pendingSubmission).
     */
    fun managerShiftStatus(
        shift: Shift,
        timesheet: Timesheet?,
        attendance: ShiftAttendance? = null,
        now: Instant = Instant.now(),
    ): ManagerShiftStatus {
        if (timesheet != null) {
            when (timesheet.status) {
                TimesheetStatus.Approved -> return ManagerShiftStatus.Approved
                TimesheetStatus.Pending -> return ManagerShiftStatus.AwaitingApproval
                TimesheetStatus.Rejected -> return ManagerShiftStatus.Rejected
                TimesheetStatus.AbsentReported, TimesheetStatus.Absent -> return ManagerShiftStatus.Absence
                TimesheetStatus.Draft -> Unit // not submitted yet — fall through
            }
        }

        if (attendance != null) {
            if (attendance.clockOutAt != null) return ManagerShiftStatus.PendingSubmission
            if (attendance.clockInAt != null && now.isBefore(shift.endDateTime)) {
                return ManagerShiftStatus.InProgress
            }
        }

        return when {
            now.isBefore(shift.startDateTime) -> ManagerShiftStatus.Scheduled
            now.isBefore(shift.endDateTime) -> ManagerShiftStatus.InProgress
            else -> ManagerShiftStatus.PendingSubmission
        }
    }
}
