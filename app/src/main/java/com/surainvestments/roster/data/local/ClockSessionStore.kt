package com.surainvestments.roster.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.surainvestments.roster.domain.model.ClockSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.clockSessionDataStore by preferencesDataStore(name = "clock_session")

/**
 * Local-only persistence for the active [ClockSession] — mirrors iOS's `clockSession.{uid}`
 * UserDefaults key exactly (see [ClockSession]'s own doc for why this is never Firestore).
 * One active session per uid; starting a new clock-in overwrites any previous one for that uid.
 *
 * DataStore-backed (not SharedPreferences) — reads/writes are necessarily suspend functions, so
 * callers must not assume a synchronous round-trip. [com.surainvestments.roster.data.repository.ClockSessionRepository]
 * is the only caller, and loads eagerly off auth state rather than lazily on first read, precisely
 * so a synchronous `StateFlow.value` read elsewhere (`SubmitHoursViewModel`'s payroll pre-fill)
 * never races an unresolved load.
 */
@Singleton
class ClockSessionStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(uid: String): ClockSession? {
        val raw = context.clockSessionDataStore.data.first()[key(uid)] ?: return null
        return runCatching { json.decodeFromString<Dto>(raw).toDomain() }.getOrNull()
    }

    suspend fun save(session: ClockSession) {
        context.clockSessionDataStore.edit { prefs ->
            prefs[key(session.staffId)] = json.encodeToString(Dto.fromDomain(session))
        }
    }

    suspend fun clear(uid: String) {
        context.clockSessionDataStore.edit { prefs -> prefs.remove(key(uid)) }
    }

    private fun key(uid: String) = stringPreferencesKey("clock_session_$uid")

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
