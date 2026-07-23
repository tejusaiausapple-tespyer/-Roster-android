package com.surainvestments.roster.domain.model

import java.time.Instant

/** Mirrors iOS `TimesheetStatus` (`Models/Enums.swift`). */
enum class TimesheetStatus(val rawValue: String) {
    Draft("draft"),
    Pending("pending"),
    Approved("approved"),
    Rejected("rejected"),
    AbsentReported("absent_reported"),
    Absent("absent"),
    ;

    companion object {
        fun fromRaw(value: String?): TimesheetStatus = entries.firstOrNull { it.rawValue == value } ?: Pending
    }
}

/**
 * `timesheets/{shiftId}` — doc id == shift id (1:1). Mirrors iOS `Timesheet`
 * (`Models/Timesheet.swift`).
 */
data class Timesheet(
    val id: String,
    val shiftId: String,
    val staffId: String,
    val actualStart: String,
    val actualEnd: String,
    val actualBreakMinutes: Int,
    val workedHours: Double,
    val staffNotes: String?,
    val status: TimesheetStatus,
    val managerNotes: String?,
    val approvedBy: String?,
    val approvedAt: String?,
    val rejectedReason: String?,
    val submittedAt: Instant?,
    val updatedAt: String?,
) {
    val isStaffReportedAbsence: Boolean get() = status == TimesheetStatus.AbsentReported

    val isStaffEditable: Boolean
        get() = status in setOf(
            TimesheetStatus.Draft,
            TimesheetStatus.Pending,
            TimesheetStatus.Rejected,
            TimesheetStatus.AbsentReported,
        )

    companion object {
        fun fromDocument(id: String, data: Map<String, Any?>): Timesheet =
            Timesheet(
                id = id,
                shiftId = data.fsString("shiftId") ?: id,
                staffId = data.fsString("staffId") ?: "",
                actualStart = data.fsString("actualStart") ?: "",
                actualEnd = data.fsString("actualEnd") ?: "",
                actualBreakMinutes = data.fsInt("actualBreakMinutes"),
                workedHours = data.fsDouble("workedHours"),
                staffNotes = data.fsString("staffNotes"),
                status = TimesheetStatus.fromRaw(data.fsString("status")),
                managerNotes = data.fsString("managerNotes"),
                approvedBy = data.fsString("approvedBy"),
                approvedAt = data.fsString("approvedAt"),
                rejectedReason = data.fsString("rejectedReason"),
                submittedAt = data.fsInstant("submittedAt"),
                updatedAt = data.fsString("updatedAt"),
            )
    }
}
