package com.surainvestments.roster.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Mirrors iOS `ShiftStatus` (`Models/Enums.swift`). */
enum class ShiftStatus(val rawValue: String) {
    Draft("draft"),
    Published("published"),
    Completed("completed"),
    Cancelled("cancelled"),
    ;

    companion object {
        fun fromRaw(value: String?): ShiftStatus = entries.firstOrNull { it.rawValue == value } ?: Draft
    }
}

/**
 * `shifts/{id}` — mirrors iOS `Shift` (`Models/Shift.swift`), the staff-relevant
 * fields of the shared `shifts` collection (same shape as src/types/index.ts).
 */
data class Shift(
    val id: String,
    val staffId: String,
    val date: String, // yyyy-MM-dd
    val rosteredStart: String, // HH:mm
    val rosteredEnd: String, // HH:mm
    val breakMinutes: Int,
    val scheduledHours: Double,
    val location: String?,
    val department: String?,
    val notes: String?,
    val status: ShiftStatus,
    val submittableAfter: Instant?,
    val shiftStartAt: Instant?,
) {
    /** Absolute start instant — [shiftStartAt] is authoritative when present, else derived from [date]+[rosteredStart]. */
    val startDateTime: Instant
        get() = shiftStartAt ?: combineDateAndTime(date, rosteredStart)

    /** Absolute end instant, derived from [date]+[rosteredEnd]; rolls to the next day for an overnight shift. */
    val endDateTime: Instant
        get() {
            val start = combineDateAndTime(date, rosteredStart)
            val end = combineDateAndTime(date, rosteredEnd)
            return if (end.isBefore(start)) end.plusSeconds(24 * 3600) else end
        }

    /** `now >= (submittableAfter ?? computed shift end)` — mirrors iOS `Shift.isSubmittable`. */
    fun isSubmittable(now: Instant = Instant.now()): Boolean =
        !now.isBefore(submittableAfter ?: endDateTime)

    companion object {
        fun fromDocument(id: String, data: Map<String, Any?>): Shift =
            Shift(
                id = id,
                staffId = data.fsString("staffId") ?: "",
                date = data.fsString("date") ?: "",
                rosteredStart = data.fsString("rosteredStart") ?: "",
                rosteredEnd = data.fsString("rosteredEnd") ?: "",
                breakMinutes = data.fsInt("breakMinutes"),
                scheduledHours = data.fsDouble("scheduledHours"),
                location = data.fsString("location"),
                department = data.fsString("department"),
                notes = data.fsString("notes"),
                status = ShiftStatus.fromRaw(data.fsString("status")),
                submittableAfter = data.fsInstant("submittableAfter"),
                shiftStartAt = data.fsInstant("shiftStartAt"),
            )

        private fun combineDateAndTime(dateKey: String, hhmm: String): Instant {
            val date = runCatching { LocalDate.parse(dateKey) }.getOrDefault(LocalDate.now(RosterCalendar.zoneId))
            val time = runCatching { LocalTime.parse(hhmm) }.getOrDefault(LocalTime.MIDNIGHT)
            return date.atTime(time).atZone(RosterCalendar.zoneId).toInstant()
        }
    }
}
