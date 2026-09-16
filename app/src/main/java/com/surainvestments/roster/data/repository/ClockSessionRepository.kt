package com.surainvestments.roster.data.repository

import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.data.local.ClockSessionStore
import com.surainvestments.roster.domain.model.ClockSession
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Reactive wrapper over the on-device [ClockSessionStore] — the single active clock-in session,
 * kept in memory as a [StateFlow] so [com.surainvestments.roster.ui.staff.home.ClockInCard] can
 * observe start/break/end taps live, mirroring iOS `RosterRepository.clockSession`.
 *
 * Loads **eagerly off [AuthRepository.authStateFlow], not lazily on the first [session] call** —
 * deliberate, because [ClockSessionStore] is DataStore-backed and therefore only readable via
 * suspend functions, while `SubmitHoursViewModel` reads [session]'s `StateFlow.value`
 * *synchronously* for its payroll pre-fill. Loading as soon as a uid resolves (well before any
 * screen could plausibly need it) keeps that synchronous read correct in practice, without forcing
 * every caller of this repository to become suspend just to accommodate the storage layer.
 */
@Singleton
class ClockSessionRepository @Inject constructor(
    private val store: ClockSessionStore,
    authRepository: AuthRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val sessionFlow = MutableStateFlow<ClockSession?>(null)

    init {
        appScope.launch {
            authRepository.authStateFlow().distinctUntilChanged().collect { uid ->
                sessionFlow.value = uid?.let { store.get(it) }
            }
        }
    }

    /** The active session for the currently signed-in user, if any — [uid] kept for call-site clarity, not used to key a fresh load (see class doc). */
    fun session(uid: String): StateFlow<ClockSession?> = sessionFlow.asStateFlow()

    /** Starts a new session for [shiftId]. No-ops if already clocked in on another shift. */
    fun start(shiftId: String, uid: String) {
        if (sessionFlow.value != null) return
        val session = ClockSession(shiftId = shiftId, staffId = uid, clockInAt = Instant.now())
        sessionFlow.value = session
        persist { store.save(session) }
    }

    fun startBreak() = update { it.startBreak() }

    fun endBreak() = update { it.endBreak() }

    /** Ends the session, recording the staff member's "stayed back" vs "used rostered end" choice. */
    fun clockOut(useRosteredEnd: Boolean) = update { it.copy(useRosteredEnd = useRosteredEnd).clockOut() }

    /** Discards the session — called once its data has landed in a submitted timesheet, or on cancel. */
    fun clear(uid: String) {
        sessionFlow.value = null
        persist { store.clear(uid) }
    }

    private fun update(transform: (ClockSession) -> ClockSession) {
        val current = sessionFlow.value ?: return
        val updated = transform(current)
        sessionFlow.value = updated
        persist { store.save(updated) }
    }

    /** Fire-and-forget persistence on the app-wide scope — the in-memory [sessionFlow] update above is what callers/UI actually observe; this just keeps disk in sync for the next cold start. */
    private fun persist(write: suspend () -> Unit) {
        appScope.launch { write() }
    }
}
