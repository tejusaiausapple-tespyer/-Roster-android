package com.surainvestments.roster.domain.model

import java.time.Instant

/** `daily_job_templates/{id}` — the permanent job library. Mirrors iOS `DailyJobTemplate`. */
data class DailyJobTemplate(
    val id: String,
    val title: String,
    val active: Boolean,
    val createdAt: Instant?,
    val createdBy: String?,
) {
    companion object {
        fun fromDocument(id: String, data: Map<String, Any?>): DailyJobTemplate =
            DailyJobTemplate(
                id = id,
                title = data.fsString("title") ?: "",
                active = data.fsBoolean("active"),
                createdAt = data.fsInstant("createdAt"),
                createdBy = data.fsString("createdBy"),
            )
    }
}

/**
 * `daily_job_assignments/{shiftId}_{templateId}` — a job template assigned to a specific
 * shift. Mirrors iOS `DailyJobAssignment` (`Models/DailyJob.swift`).
 */
data class DailyJobAssignment(
    val id: String,
    val shiftId: String,
    val staffId: String,
    val templateId: String,
    val title: String, // snapshot at assignment time — template edits don't rewrite history
    val date: String, // shift date, yyyy-MM-dd
    val assignedAt: Instant?,
    val assignedBy: String?,
    val completed: Boolean,
    val completedAt: Instant?,
    val completedBy: String?,
) {
    companion object {
        fun docId(shiftId: String, templateId: String): String = "${shiftId}_$templateId"

        fun fromDocument(id: String, data: Map<String, Any?>): DailyJobAssignment =
            DailyJobAssignment(
                id = id,
                shiftId = data.fsString("shiftId") ?: "",
                staffId = data.fsString("staffId") ?: "",
                templateId = data.fsString("templateId") ?: "",
                title = data.fsString("title") ?: "",
                date = data.fsString("date") ?: "",
                assignedAt = data.fsInstant("assignedAt"),
                assignedBy = data.fsString("assignedBy"),
                completed = data.fsBoolean("completed"),
                completedAt = data.fsInstant("completedAt"),
                completedBy = data.fsString("completedBy"),
            )
    }
}
