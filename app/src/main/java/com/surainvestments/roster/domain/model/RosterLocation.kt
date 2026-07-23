package com.surainvestments.roster.domain.model

/**
 * A manager-defined work location: suburb + Australian state, with an optional geofence anchor
 * for shift-attendance verification. Stored as an array on `settings/locations`. Mirrors iOS
 * `RosterLocation` (`Models/RosterLocation.swift`).
 */
data class RosterLocation(
    val suburb: String,
    val state: String, // e.g. "SA"
    val city: String,
    val latitude: Double?,
    val longitude: Double?,
    val geofenceRadius: Double?, // metres
    val geofenceEnforced: Boolean = false,
) {
    val hasGeofence: Boolean get() = latitude != null && longitude != null

    val effectiveGeofenceRadius: Double get() = geofenceRadius ?: defaultGeofenceRadius

    /** The string written to `shifts.location` — matched against this to resolve a shift's workplace. */
    val displayName: String get() = "$suburb, $state"

    companion object {
        /** Fallback geofence radius when a location has coordinates but no explicit radius saved. */
        const val defaultGeofenceRadius: Double = 250.0

        fun fromMap(data: Map<String, Any?>): RosterLocation? {
            val suburb = data["suburb"] as? String ?: return null
            val state = data["state"] as? String ?: return null
            if (suburb.isBlank() || state.isBlank()) return null
            return RosterLocation(
                suburb = suburb,
                state = state,
                city = data["city"] as? String ?: capital(state),
                latitude = (data["latitude"] as? Number)?.toDouble(),
                longitude = (data["longitude"] as? Number)?.toDouble(),
                geofenceRadius = (data["geofenceRadius"] as? Number)?.toDouble(),
                geofenceEnforced = data["geofenceEnforced"] as? Boolean ?: false,
            )
        }

        fun capital(state: String): String = when (state) {
            "NSW" -> "Sydney"
            "VIC" -> "Melbourne"
            "QLD" -> "Brisbane"
            "SA" -> "Adelaide"
            "WA" -> "Perth"
            "TAS" -> "Hobart"
            "NT" -> "Darwin"
            "ACT" -> "Canberra"
            else -> ""
        }
    }
}
