package com.surainvestments.roster.data.repository

import com.surainvestments.roster.data.remote.SaveStaffAvailabilityRequest
import com.surainvestments.roster.data.remote.WorkerApiService
import com.surainvestments.roster.data.remote.bodyOrThrow
import com.surainvestments.roster.domain.model.UserAvailability
import com.surainvestments.roster.domain.model.Weekday
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `POST /api/staff/availability` — availability is **never** a direct Firestore write (even
 * though the rules would technically allow a self-write gap, `weeklyAvailability` is deliberately
 * excluded from `isValidSelfUserUpdate`'s allow-list): only the Worker's Admin-SDK credentials may
 * write it, so the week-lock check is enforced against the server's trusted clock, not the
 * device's. Mirrors iOS `WorkerAPIClient.saveAvailability`.
 */
@Singleton
class AvailabilityRepository @Inject constructor(private val workerApi: WorkerApiService) {

    /** Throws [com.surainvestments.roster.domain.model.WorkerApiException] with the server's own message (e.g. a locked-week rejection). */
    suspend fun save(userId: String, weeklyAvailability: Map<String, UserAvailability>) {
        val body = SaveStaffAvailabilityRequest(userId = userId, weeklyAvailability = weeklyAvailability.toJson())
        workerApi.saveStaffAvailability(body).bodyOrThrow()
    }

    private fun Map<String, UserAvailability>.toJson(): JsonObject = buildJsonObject {
        forEach { (weekKey, week) ->
            put(
                weekKey,
                buildJsonObject {
                    Weekday.entries.forEach { weekday ->
                        val day = week[weekday]
                        put(
                            weekday.rawValue,
                            buildJsonObject {
                                put("available", day.available)
                                put("allDay", day.allDay)
                                day.start?.let { put("start", it) }
                                day.end?.let { put("end", it) }
                            },
                        )
                    }
                },
            )
        }
    }
}
