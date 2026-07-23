package com.surainvestments.roster.domain.model

import java.time.Duration
import java.time.Instant

/**
 * A staff member's live clock-in session for one shift, tracked **on device only** — mirrors
 * iOS `ClockSession` (`Models/ClockSession.swift`).
 *
 * WHY LOCAL: the deployed Firestore rules only allow a staff member to create/update
 * `timesheets/{shiftId}` once `request.time >= shift.submittableAfter` (i.e. after the shift
 * ends), so a real-time clock-in can't be written to the backend as it happens. This session is
 * persisted locally (survives process death mid-shift) and its recorded times/breaks seed the
 * timesheet at Submit Hours — that's where the data actually enters the system of record.
 *
 * Immutable/copy-based (idiomatic Kotlin) rather than iOS's `mutating func` style — callers use
 * [startBreak]/[endBreak]/[clockOut] to get an updated copy, then persist it via the store.
 */
data class ClockSession(
    val shiftId: String,
    val staffId: String,
    val clockInAt: Instant,
    val clockOutAt: Instant? = null,
    val breaks: List<BreakInterval> = emptyList(),
    /**
     * Staff's choice when ending at/after the rostered end: true = "use my rostered end time"
     * (submit seeds the roster's end), false/null = "stayed back for extra work" (submit seeds
     * the actual clock-out, editable).
     */
    val useRosteredEnd: Boolean? = null,
) {
    /** One recorded break. [end] is null while the break is in progress. */
    data class BreakInterval(val start: Instant, val end: Instant? = null) {
        fun duration(now: Instant = Instant.now()): Duration {
            val effectiveEnd = end ?: now
            return if (effectiveEnd.isAfter(start)) Duration.between(start, effectiveEnd) else Duration.ZERO
        }
    }

    val isOnBreak: Boolean get() = breaks.lastOrNull()?.end == null && breaks.isNotEmpty()
    val isActive: Boolean get() = clockOutAt == null

    /** Total recorded break time. An in-progress break counts up to [now]. */
    fun totalBreakSeconds(now: Instant = Instant.now()): Long =
        breaks.sumOf { it.duration(now).seconds }

    /**
     * Break minutes for the timesheet: rounded to the nearest [BusinessRules.breakMinutesStep]
     * and clamped to the business range, so it slots straight into the existing break stepper.
     */
    fun timesheetBreakMinutes(now: Instant = Instant.now()): Int {
        val minutes = totalBreakSeconds(now) / 60.0
        val step = BusinessRules.breakMinutesStep.toDouble()
        val rounded = (Math.round(minutes / step) * step).toInt()
        return BusinessRules.clampBreakMinutes(rounded)
    }

    /** Elapsed on-the-clock time (excludes breaks). */
    fun workedSeconds(now: Instant = Instant.now()): Long {
        val end = clockOutAt ?: now
        val raw = Duration.between(clockInAt, end).seconds - totalBreakSeconds(now)
        return maxOf(0, raw)
    }

    /**
     * Paid working time: an early check-in's pre-shift minutes are excluded — paid time begins
     * at [rosterStart], not actual clock-in — and only breaks overlapping that paid window are
     * deducted.
     */
    fun paidWorkedSeconds(rosterStart: Instant, now: Instant = Instant.now()): Long {
        val paidStart = paidStart(rosterStart)
        val end = clockOutAt ?: now
        if (!end.isAfter(paidStart)) return 0
        val breakOverlapSeconds = breaks.sumOf { brk ->
            val overlapStart = maxOf(brk.start, paidStart)
            val overlapEnd = minOf(brk.end ?: now, end)
            if (overlapEnd.isAfter(overlapStart)) Duration.between(overlapStart, overlapEnd).seconds else 0
        }
        return Duration.between(paidStart, end).seconds - breakOverlapSeconds
    }

    /** The instant paid time begins for a given rostered start. */
    fun paidStart(rosterStart: Instant): Instant = maxOf(clockInAt, rosterStart)

    fun startBreak(now: Instant = Instant.now()): ClockSession {
        if (!isActive || isOnBreak) return this
        return copy(breaks = breaks + BreakInterval(start = now))
    }

    fun endBreak(now: Instant = Instant.now()): ClockSession {
        if (!isOnBreak) return this
        val last = breaks.lastIndex
        return copy(breaks = breaks.toMutableList().also { it[last] = it[last].copy(end = now) })
    }

    fun clockOut(now: Instant = Instant.now()): ClockSession =
        endBreak(now).copy(clockOutAt = now)
}
