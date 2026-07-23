package com.surainvestments.roster.domain.model

import android.location.Location
import com.google.firebase.firestore.GeoPoint
import java.time.Instant

/** Mirrors iOS `ShiftAttendance.GeofenceStatus`. */
enum class GeofenceStatus(val rawValue: String) {
    Inside("inside"),
    Outside("outside"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromRaw(value: String?): GeofenceStatus = entries.firstOrNull { it.rawValue == value } ?: Unknown
    }
}

/** A single clock-in/out GPS reading. Mirrors iOS `ShiftAttendance.Fix`. */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double, // metres
    val geofence: GeofenceStatus,
    val distanceFromWorkplace: Double?, // metres
) {
    companion object {
        /**
         * Evaluate a GPS fix against the shift's saved workplace geofence. The fix's accuracy is
         * added to the allowed radius so staff with a weak signal at the right place aren't
         * flagged as outside. [allowedRadius] overrides the workplace's configured radius (used
         * for the lenient 250 m allowance when enforcement is off). Mirrors iOS
         * `ShiftAttendance.Fix.init(location:workplace:allowedRadius:)`.
         */
        fun from(location: Location, workplace: RosterLocation?, allowedRadius: Double? = null): Fix {
            val accuracy = maxOf(0f, location.accuracy).toDouble()
            val lat = workplace?.latitude
            val lon = workplace?.longitude
            if (workplace != null && lat != null && lon != null) {
                val results = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, lat, lon, results)
                val distance = results[0].toDouble()
                val allowed = (allowedRadius ?: workplace.effectiveGeofenceRadius) + accuracy
                return Fix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = accuracy,
                    geofence = if (distance <= allowed) GeofenceStatus.Inside else GeofenceStatus.Outside,
                    distanceFromWorkplace = distance,
                )
            }
            return Fix(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = accuracy,
                geofence = GeofenceStatus.Unknown,
                distanceFromWorkplace = null,
            )
        }
    }
}

/**
 * `shift_attendance/{shiftId}` — the tamper-resistant record managers see: `clockInAt`/
 * `clockOutAt` are server timestamps (can't be forged from the device clock), while the
 * paired `clockInDeviceAt`/`clockOutDeviceAt` capture the device's own clock at that same
 * instant so a large gap between the two exposes clock manipulation. GPS [Fix]es are
 * checked against the shift's workplace geofence. Distinct from [Timesheet], which is the
 * staff-editable, manager-approved record — this is the automatic, verified one written by
 * the clock-in/out action itself. Mirrors iOS `ShiftAttendance` (`Models/ShiftAttendance.swift`).
 */
data class ShiftAttendance(
    val id: String, // == shiftId
    val shiftId: String,
    val staffId: String,
    val date: String, // yyyy-MM-dd
    val location: String?,
    val clockInAt: Instant?,
    val clockInDeviceAt: Instant?,
    val clockInFix: Fix?,
    val clockOutAt: Instant?,
    val clockOutDeviceAt: Instant?,
    val clockOutFix: Fix?,
    val clockOutNote: String?,
) {
    companion object {
        fun fromDocument(id: String, data: Map<String, Any?>): ShiftAttendance? {
            val staffId = data.fsString("staffId") ?: return null
            return ShiftAttendance(
                id = id,
                shiftId = data.fsString("shiftId") ?: id,
                staffId = staffId,
                date = data.fsString("date") ?: "",
                location = data.fsString("location"),
                clockInAt = data.fsInstant("clockInAt"),
                clockInDeviceAt = data.fsInstant("clockInDeviceAt"),
                clockInFix = fix(data, "clockIn"),
                clockOutAt = data.fsInstant("clockOutAt"),
                clockOutDeviceAt = data.fsInstant("clockOutDeviceAt"),
                clockOutFix = fix(data, "clockOut"),
                clockOutNote = data.fsString("clockOutNote"),
            )
        }

        private fun fix(data: Map<String, Any?>, prefix: String): Fix? {
            val point = data["${prefix}Location"] as? GeoPoint ?: return null
            return Fix(
                latitude = point.latitude,
                longitude = point.longitude,
                accuracy = data.fsDouble("${prefix}AccuracyM"),
                geofence = GeofenceStatus.fromRaw(data.fsString("${prefix}Geofence")),
                distanceFromWorkplace = (data["${prefix}DistanceM"] as? Number)?.toDouble(),
            )
        }

        /** Field payload for one clock event (start or end). Mirrors iOS `ShiftAttendance.fixFields`. */
        fun fixFields(prefix: String, fix: Fix?): Map<String, Any> {
            if (fix == null) return mapOf("${prefix}Geofence" to GeofenceStatus.Unknown.rawValue)
            val fields = mutableMapOf<String, Any>(
                "${prefix}Location" to GeoPoint(fix.latitude, fix.longitude),
                "${prefix}AccuracyM" to fix.accuracy,
                "${prefix}Geofence" to fix.geofence.rawValue,
            )
            fix.distanceFromWorkplace?.let { fields["${prefix}DistanceM"] = it }
            return fields
        }
    }
}
