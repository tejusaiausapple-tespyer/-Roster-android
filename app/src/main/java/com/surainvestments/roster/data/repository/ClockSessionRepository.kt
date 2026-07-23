package com.surainvestments.roster.data.repository

import com.surainvestments.roster.data.local.ClockSessionStore
import com.surainvestments.roster.domain.model.ClockSession
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive wrapper over the on-device [ClockSessionStore] — the single active clock-in session,
 * kept in memory as a [StateFlow] so [com.surainvestments.roster.ui.staff.home.ClockInCard] can
 * observe start/break/end taps live, mirroring iOS `RosterRepository.clockSession`.
 */
@Singleton
class ClockSessionRepository @Inject constructor(private val store: ClockSessionStore) {
    private val sessionFlow = MutableStateFlow<ClockSession?>(null)
    private var loadedUid: String? = null

    /** Loads (once per uid) and exposes the active session for [uid], if any. */
    fun session(uid: String): StateFlow<ClockSession?> {
        if (loadedUid != uid) {
            loadedUid = uid
            sessionFlow.value = store.get(uid)
        }
        return sessionFlow.asStateFlow()
    }

    /** Starts a new session for [shiftId]. No-ops if already clocked in on another shift. */
    fun start(shiftId: String, uid: String) {
        if (sessionFlow.value != null) return
        val session = ClockSession(shiftId = shiftId, staffId = uid, clockInAt = Instant.now())
        sessionFlow.value = session
        store.save(session)
    }

    fun startBreak() = update { it.startBreak() }

    fun endBreak() = update { it.endBreak() }

    /** Ends the session, recording the staff member's "stayed back" vs "used rostered end" choice. */
    fun clockOut(useRosteredEnd: Boolean) = update { it.copy(useRosteredEnd = useRosteredEnd).clockOut() }

    /** Discards the session — called once its data has landed in a submitted timesheet, or on cancel. */
    fun clear(uid: String) {
        sessionFlow.value = null
        store.clear(uid)
    }

    private fun update(transform: (ClockSession) -> ClockSession) {
        val current = sessionFlow.value ?: return
        val updated = transform(current)
        sessionFlow.value = updated
        store.save(updated)
    }
}
