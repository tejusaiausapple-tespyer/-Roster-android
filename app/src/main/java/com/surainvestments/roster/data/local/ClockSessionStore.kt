package com.surainvestments.roster.data.local

import android.content.Context
import androidx.core.content.edit
import com.surainvestments.roster.domain.model.ClockSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local-only persistence for the active [ClockSession] — mirrors iOS's `clockSession.{uid}`
 * UserDefaults key exactly (see [ClockSession]'s own doc for why this is never Firestore).
 * One active session per uid; starting a new clock-in overwrites any previous one for that uid.
 */
@Singleton
class ClockSessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("clock_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun get(uid: String): ClockSession? {
        val raw = prefs.getString(key(uid), null) ?: return null
        return runCatching { json.decodeFromString<Dto>(raw).toDomain() }.getOrNull()
    }

    fun save(session: ClockSession) {
        prefs.edit { putString(key(session.staffId), json.encodeToString(Dto.fromDomain(session))) }
    }

    fun clear(uid: String) {
        prefs.edit { remove(key(uid)) }
    }

    private fun key(uid: String) = "clock_session_$uid"

    @Serializable
    private data class Dto(
        val shiftId: String,
        val staffId: String,
        val clockInAtEpochMs: Long,
        val clockOutAtEpochMs: Long? = null,
        val breaks: List<BreakDto> = emptyList(),
        val useRosteredEnd: Boolean? = null,
    ) {
        fun toDomain(): ClockSession = ClockSession(
            shiftId = shiftId,
            staffId = staffId,
            clockInAt = Instant.ofEpochMilli(clockInAtEpochMs),
            clockOutAt = clockOutAtEpochMs?.let { Instant.ofEpochMilli(it) },
            breaks = breaks.map { it.toDomain() },
            useRosteredEnd = useRosteredEnd,
        )

        companion object {
            fun fromDomain(session: ClockSession): Dto = Dto(
                shiftId = session.shiftId,
                staffId = session.staffId,
                clockInAtEpochMs = session.clockInAt.toEpochMilli(),
                clockOutAtEpochMs = session.clockOutAt?.toEpochMilli(),
                breaks = session.breaks.map { BreakDto.fromDomain(it) },
                useRosteredEnd = session.useRosteredEnd,
            )
        }
    }

    @Serializable
    private data class BreakDto(val startEpochMs: Long, val endEpochMs: Long? = null) {
        fun toDomain(): ClockSession.BreakInterval = ClockSession.BreakInterval(
            start = Instant.ofEpochMilli(startEpochMs),
            end = endEpochMs?.let { Instant.ofEpochMilli(it) },
        )

        companion object {
            fun fromDomain(interval: ClockSession.BreakInterval): BreakDto = BreakDto(
                startEpochMs = interval.start.toEpochMilli(),
                endEpochMs = interval.end?.toEpochMilli(),
            )
        }
    }
}
