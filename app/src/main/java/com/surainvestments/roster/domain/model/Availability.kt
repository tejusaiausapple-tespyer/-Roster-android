package com.surainvestments.roster.domain.model

/** Monday-first, matching the Firestore key set exactly. Mirrors iOS `Weekday`. */
enum class Weekday(val rawValue: String, val fullLabel: String, val shortLabel: String) {
    Monday("monday", "Monday", "Mon"),
    Tuesday("tuesday", "Tuesday", "Tue"),
    Wednesday("wednesday", "Wednesday", "Wed"),
    Thursday("thursday", "Thursday", "Thu"),
    Friday("friday", "Friday", "Fri"),
    Saturday("saturday", "Saturday", "Sat"),
    Sunday("sunday", "Sunday", "Sun"),
    ;

    companion object {
        fun fromRaw(value: String?): Weekday? = entries.firstOrNull { it.rawValue == value }
    }
}

/**
 * One day's availability. `start`/`end` ("HH:mm") are only meaningful (and only ever persisted)
 * when `available && !allDay`. Mirrors iOS `DayAvailability` (`Models/Availability.swift`).
 */
data class DayAvailability(
    val available: Boolean = true,
    val allDay: Boolean = true,
    val start: String? = null,
    val end: String? = null,
) {
    companion object {
        val defaultDay = DayAvailability(available = true, allDay = true, start = "09:00", end = "17:00")

        fun fromMap(data: Map<String, Any?>?): DayAvailability {
            if (data == null) return defaultDay
            return DayAvailability(
                available = data["available"] as? Boolean ?: true,
                allDay = data["allDay"] as? Boolean ?: true,
                start = data["start"] as? String,
                end = data["end"] as? String,
            )
        }
    }

    /** Only `start`/`end` present when set — omitted (not null), matching iOS `asDictionary`. */
    fun asMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>("available" to available, "allDay" to allDay)
        start?.let { map["start"] = it }
        end?.let { map["end"] = it }
        return map
    }
}

/** All 7 days for one week. Missing days fall back to [DayAvailability.defaultDay]. Mirrors iOS `UserAvailability`. */
data class UserAvailability(val days: Map<Weekday, DayAvailability> = emptyMap()) {
    operator fun get(weekday: Weekday): DayAvailability = days[weekday] ?: DayAvailability.defaultDay

    fun with(weekday: Weekday, value: DayAvailability): UserAvailability = UserAvailability(days + (weekday to value))

    fun asMap(): Map<String, Any> = Weekday.entries.associate { it.rawValue to this[it].asMap() }

    companion object {
        val default = UserAvailability(Weekday.entries.associateWith { DayAvailability.defaultDay })

        @Suppress("UNCHECKED_CAST")
        fun fromMap(data: Map<String, Any?>?): UserAvailability {
            if (data == null) return default
            val days = Weekday.entries.associateWith { weekday ->
                DayAvailability.fromMap(data[weekday.rawValue] as? Map<String, Any?>)
            }
            return UserAvailability(days)
        }
    }
}
