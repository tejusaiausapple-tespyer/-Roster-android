package com.surainvestments.roster.domain.model

import java.time.Instant

/** `task_completions/{taskId}_{date}` — mirrors iOS `TaskCompletion` (`Models/RosterTask.swift`). */
data class TaskCompletion(
    val id: String,
    val taskId: String,
    val date: String, // yyyy-MM-dd
    val completed: Boolean,
    val completedAt: Instant?,
    val completedBy: String?,
    val staffPhotoUrl: String?, // legacy single photo
    val staffPhotoUrls: List<String>?,
    val note: String?,
    val status: String?, // "completed" | "redo"
    val redoReason: String?,
    val reviewedBy: String?,
    val reviewedAt: Instant?,
    val managerDownloadedAt: Instant?,
) {
    val isRedoRequested: Boolean get() = status == "redo"

    /** Tolerates legacy single-photo docs — prefers [staffPhotoUrls], falls back to [staffPhotoUrl]. */
    val photoUrls: List<String>
        get() = staffPhotoUrls?.takeIf { it.isNotEmpty() }
            ?: staffPhotoUrl?.takeIf { it.isNotBlank() }?.let { listOf(it) }
            ?: emptyList()

    companion object {
        fun fromDocument(id: String, data: Map<String, Any?>): TaskCompletion =
            TaskCompletion(
                id = id,
                taskId = data.fsString("taskId") ?: "",
                date = data.fsString("date") ?: "",
                completed = data.fsBoolean("completed"),
                completedAt = data.fsInstant("completedAt"),
                completedBy = data.fsString("completedBy"),
                staffPhotoUrl = data.fsString("staffPhotoUrl"),
                staffPhotoUrls = data.fsStringList("staffPhotoUrls"),
                note = data.fsString("note"),
                status = data.fsString("status"),
                redoReason = data.fsString("redoReason"),
                reviewedBy = data.fsString("reviewedBy"),
                reviewedAt = data.fsInstant("reviewedAt"),
                managerDownloadedAt = data.fsInstant("managerDownloadedAt"),
            )
    }
}
