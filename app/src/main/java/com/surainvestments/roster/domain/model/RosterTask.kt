package com.surainvestments.roster.domain.model

import java.time.Instant

/** Mirrors iOS `TaskPriority` (`Models/RosterTask.swift`). */
enum class TaskPriority(val rawValue: String, val weight: Int) {
    High("high", 0),
    Normal("normal", 1),
    Low("low", 2),
    ;

    companion object {
        fun fromRaw(value: String?): TaskPriority = entries.firstOrNull { it.rawValue == value } ?: Normal
    }
}

/** `tasks/{id}` — mirrors iOS `RosterTask` (`Models/RosterTask.swift`). */
data class RosterTask(
    val id: String,
    val title: String,
    val description: String?,
    val managerPhotoUrl: String?,
    val frequency: String, // "once" | "daily" | "weekly"
    val date: String?, // yyyy-MM-dd, only when frequency == "once"
    val dayOfWeek: List<Int>?, // 1=Monday..7=Sunday, only when frequency == "weekly"
    val active: Boolean,
    val createdAt: Instant?,
    val createdBy: String?,
    val assignedTo: List<String>?, // staff uids; null/empty = all staff
    val dueTime: String?, // HH:mm
    val priority: String?,
    val requiresPhoto: Boolean?, // null defaults to true (legacy)
    val endDate: String?, // yyyy-MM-dd
) {
    val priorityLevel: TaskPriority get() = TaskPriority.fromRaw(priority)
    val photoRequired: Boolean get() = requiresPhoto ?: true

    /** Whether this task is scheduled on [dayKey] (weekday 1=Mon..7=Sun). Mirrors iOS `RosterTask.isActive`. */
    fun isActive(onDayKey: String, weekday: Int): Boolean {
        if (!active) return false
        if (endDate != null && onDayKey > endDate) return false
        return when (frequency) {
            "once" -> date == onDayKey
            "weekly" -> dayOfWeek?.contains(weekday) ?: false
            else -> true // "daily" and any unrecognised value
        }
    }

    /** Whether this task is assigned to [userId] (null/empty [assignedTo] = everyone). Mirrors iOS `RosterTask.isAssigned(to:)`. */
    fun isAssigned(userId: String?): Boolean {
        if (assignedTo.isNullOrEmpty()) return true
        if (userId == null) return false
        return assignedTo.contains(userId)
    }

    companion object {
        fun fromDocument(id: String, data: Map<String, Any?>): RosterTask =
            RosterTask(
                id = id,
                title = data.fsString("title") ?: "",
                description = data.fsString("description"),
                managerPhotoUrl = data.fsString("managerPhotoUrl"),
                frequency = data.fsString("frequency") ?: "daily",
                date = data.fsString("date"),
                dayOfWeek = data.fsIntList("dayOfWeek"),
                active = data.fsBoolean("active"),
                createdAt = data.fsInstant("createdAt"),
                createdBy = data.fsString("createdBy"),
                assignedTo = data.fsStringList("assignedTo"),
                dueTime = data.fsString("dueTime"),
                priority = data.fsString("priority"),
                requiresPhoto = data["requiresPhoto"] as? Boolean,
                endDate = data.fsString("endDate"),
            )
    }
}
