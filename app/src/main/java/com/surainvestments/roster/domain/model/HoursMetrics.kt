package com.surainvestments.roster.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Approved-hours rollups by period, plus pending/rejected counts — shown on Home and Account.
 * Buckets by *shift date* (not submission date), Australia/Adelaide. Mirrors iOS `HoursMetrics`
 * (`Features/Shared/HoursMetrics.swift`).
 */
data class HoursMetrics(
    val week: Double = 0.0,
    val month: Double = 0.0,
    val year: Double = 0.0,
    val all: Double = 0.0,
    val pendingHours: Double = 0.0,
    val pendingCount: Int = 0,
    val rejectedCount: Int = 0,
) {
    companion object {
        fun compute(timesheets: List<Timesheet>, shifts: List<Shift>, now: Instant = Instant.now()): HoursMetrics {
            val nowZoned = ZonedDateTime.ofInstant(now, RosterCalendar.zoneId)
            val nowWeekKey = RosterCalendar.weekStartKey(now)
            val shiftDateByShiftId = shifts.associate { it.id to it.date }

            var week = 0.0
            var month = 0.0
            var year = 0.0
            var all = 0.0
            var pendingHours = 0.0
            var pendingCount = 0
            var rejectedCount = 0

            for (ts in timesheets) {
                when (ts.status) {
                    TimesheetStatus.Approved -> {
                        all += ts.workedHours
                        // Bucket by shift date when the shift is loaded; otherwise fall back to
                        // the submission time. Staff shifts are only kept for a −28…+56-day
                        // window, so older approved timesheets have no matching shift — without
                        // the fallback they'd silently drop out of the week/month/year buckets.
                        val resolvedDate: LocalDate = shiftDateByShiftId[ts.shiftId]?.let { RosterCalendar.parseDateKey(it) }
                            ?: ts.submittedAt?.atZone(RosterCalendar.zoneId)?.toLocalDate()
                            ?: continue
                        if (resolvedDate.year == nowZoned.year) {
                            year += ts.workedHours
                            if (resolvedDate.monthValue == nowZoned.monthValue) month += ts.workedHours
                        }
                        val dateInstant = resolvedDate.atStartOfDay(RosterCalendar.zoneId).toInstant()
                        if (RosterCalendar.weekStartKey(dateInstant) == nowWeekKey) {
                            week += ts.workedHours
                        }
                    }
                    TimesheetStatus.Pending -> {
                        pendingHours += ts.workedHours
                        pendingCount += 1
                    }
                    TimesheetStatus.Rejected -> rejectedCount += 1
                    else -> Unit
                }
            }

            return HoursMetrics(
                week = week,
                month = month,
                year = year,
                all = all,
                pendingHours = pendingHours,
                pendingCount = pendingCount,
                rejectedCount = rejectedCount,
            )
        }
    }
}
