package com.surainvestments.roster.domain.model

/**
 * The manager-facing lifecycle status of a shift, derived by [BusinessRules.managerShiftStatus].
 * Mirrors iOS `ManagerShiftStatus` (`Models/Enums.swift`).
 */
enum class ManagerShiftStatus {
    Scheduled, // shift has not started yet
    InProgress, // now is within the scheduled window
    PendingSubmission, // shift ended, staff has not submitted hours
    AwaitingApproval, // timesheet submitted, waiting on the manager
    Approved, // manager approved — shift complete
    Rejected, // manager rejected — staff must resubmit
    Absence, // staff reported (or manager confirmed) an absence
    ;

    val title: String
        get() = when (this) {
            Scheduled -> "Scheduled"
            InProgress -> "In Progress"
            PendingSubmission -> "Pending"
            AwaitingApproval -> "Awaiting Approval"
            Approved -> "Approved"
            Rejected -> "Rejected"
            Absence -> "Absent"
        }
}
