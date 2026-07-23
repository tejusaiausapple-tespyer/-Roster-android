package com.surainvestments.roster.domain.model

/**
 * The staff-facing display status of a shift, derived by [BusinessRules.displayStatus] —
 * distinct from [ManagerShiftStatus], which collapses `absent_reported`/`absent` into one
 * "Absence" bucket and never surfaces `draft`/`pending` verbatim. Mirrors iOS
 * `StaffShiftDisplayStatus` (`Models/Enums.swift`).
 */
enum class StaffShiftDisplayStatus(val rawValue: String) {
    Scheduled("scheduled"),
    AwaitingSubmission("awaiting_submission"),
    Draft("draft"),
    Pending("pending"),
    Approved("approved"),
    Rejected("rejected"),
    AbsentReported("absent_reported"),
    Absent("absent"),
    ;

    val title: String
        get() = when (this) {
            Scheduled -> "Scheduled"
            AwaitingSubmission -> "Awaiting submission"
            Draft -> "Draft"
            Pending -> "Pending review"
            Approved -> "Approved"
            Rejected -> "Rejected"
            AbsentReported -> "Absence reported"
            Absent -> "Absent"
        }

    companion object {
        fun fromRaw(value: String?): StaffShiftDisplayStatus? = entries.firstOrNull { it.rawValue == value }
    }
}
