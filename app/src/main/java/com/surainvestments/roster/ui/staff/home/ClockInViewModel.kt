package com.surainvestments.roster.ui.staff.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.ClockSessionRepository
import com.surainvestments.roster.data.repository.LocationsRepository
import com.surainvestments.roster.data.repository.ShiftAttendanceRepository
import com.surainvestments.roster.data.repository.ShiftRepository
import com.surainvestments.roster.data.service.LocationService
import com.surainvestments.roster.data.service.ServerClock
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.ClockSession
import com.surainvestments.roster.domain.model.Fix
import com.surainvestments.roster.domain.model.GeofenceStatus
import com.surainvestments.roster.domain.model.Shift
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Outcome of a start/end attempt — the Composable branches on this to decide which dialog (if any) to show. */
sealed interface ClockAttemptResult {
    data object Committed : ClockAttemptResult
    data class NeedsConfirmation(val fix: Fix?, val message: String, val confirmLabel: String) : ClockAttemptResult
    data class Blocked(val message: String) : ClockAttemptResult
    data class Failed(val message: String) : ClockAttemptResult
}

/**
 * Start/break/end orchestration for [ClockInCard]. The device-local [ClockSession] drives the
 * live timer and prefills `SubmitHoursSheet`; alongside it, every start/end tap writes a
 * verified [com.surainvestments.roster.domain.model.ShiftAttendance] record — server-authoritative
 * timestamps plus a GPS fix checked against the shift's workplace geofence. Mirrors iOS `ClockInCard`'s
 * `capture`/`commit` orchestration (there embedded in the view; split out here as a ViewModel is
 * the idiomatic Compose equivalent).
 */
@HiltViewModel
class ClockInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val clockSessionRepository: ClockSessionRepository,
    private val shiftAttendanceRepository: ShiftAttendanceRepository,
    private val shiftRepository: ShiftRepository,
    private val locationsRepository: LocationsRepository,
    private val locationService: LocationService,
    private val serverClock: ServerClock,
) : ViewModel() {

    /**
     * If a manager deletes the shift a session is tied to before the staff member submits hours
     * for it, the session becomes orphaned — it can never be submitted, and (worse) its `shiftId`
     * no longer matching any real shift makes every other shift look "busy on another shift" and
     * blocks their Start button too. Cross-referencing against the live shift window and clearing
     * on mismatch is what makes that self-heal instead of getting stuck until the process restarts.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<ClockSession?> = authRepository.authStateFlow()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(null)
            } else {
                combine(clockSessionRepository.session(uid), shiftRepository.staffShiftsById(uid)) { session, shiftsById -> session to shiftsById }
                    .onEach { (session, shiftsById) ->
                        if (session != null && shiftsById.isNotEmpty() && !shiftsById.containsKey(session.shiftId)) {
                            clockSessionRepository.clear(uid)
                        }
                    }
                    .map { (session, shiftsById) -> session?.takeIf { shiftsById.isEmpty() || shiftsById.containsKey(it.shiftId) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { serverClock.sync() }
    }

    fun serverNow(): Instant = serverClock.now()

    fun hasLocationPermission(): Boolean = locationService.hasPermission()

    fun startBreak() = clockSessionRepository.startBreak()

    fun endBreak() = clockSessionRepository.endBreak()

    /**
     * START, geofence enforced: outside the configured radius blocks. Not enforced: allowed
     * within [BusinessRules.lenientStartRadiusMetres]; further out warns-and-records but can proceed.
     */
    suspend fun attemptStart(shift: Shift): ClockAttemptResult {
        val uid = authRepository.currentUid() ?: return ClockAttemptResult.Failed("Not signed in.")
        val workplace = locationsRepository.workplace(shift)
        val enforced = workplace?.geofenceEnforced ?: false
        val location = runCatching { locationService.currentLocation() }.getOrNull()

        if (location == null) {
            return ClockAttemptResult.NeedsConfirmation(
                fix = null,
                message = "Your location couldn't be determined, so it won't be verified if you start the shift now.",
                confirmLabel = "Start Anyway",
            )
        }

        val fix = Fix.from(
            location = location,
            workplace = workplace,
            allowedRadius = if (enforced) null else BusinessRules.lenientStartRadiusMetres,
        )

        if (fix.geofence == GeofenceStatus.Outside && workplace != null) {
            val distance = fix.distanceFromWorkplace?.let(::formatDistance) ?: "some distance"
            if (enforced) {
                return ClockAttemptResult.Blocked(
                    "You appear to be $distance from ${workplace.displayName}. Move inside the work zone to start your shift.",
                )
            }
            return ClockAttemptResult.NeedsConfirmation(
                fix = fix,
                message = "You appear to be $distance from ${workplace.displayName}. This will be recorded on your attendance.",
                confirmLabel = "Start Anyway",
            )
        }

        return commitStart(shift, uid, fix)
    }

    /** Commits a start the caller has already decided to proceed with (e.g. after a geofence-prompt confirmation). */
    suspend fun confirmStart(shift: Shift, fix: Fix?): ClockAttemptResult {
        val uid = authRepository.currentUid() ?: return ClockAttemptResult.Failed("Not signed in.")
        return commitStart(shift, uid, fix)
    }

    private suspend fun commitStart(shift: Shift, uid: String, fix: Fix?): ClockAttemptResult = try {
        clockSessionRepository.start(shift.id, uid)
        shiftAttendanceRepository.startShift(shift, uid, fix)
        ClockAttemptResult.Committed
    } catch (e: Exception) {
        ClockAttemptResult.Failed(
            "Your shift was recorded on this device, but the verified time couldn't reach the server: ${e.message}",
        )
    }

    /** END is never location-restricted — the fix is recorded for the audit trail only (best-effort). */
    suspend fun endShift(shift: Shift, note: String?, useRosteredEnd: Boolean): ClockAttemptResult {
        val workplace = locationsRepository.workplace(shift)
        val location = runCatching { locationService.currentLocation() }.getOrNull()
        val fix = location?.let { Fix.from(it, workplace) }
        return try {
            clockSessionRepository.clockOut(useRosteredEnd)
            shiftAttendanceRepository.endShift(shift, fix, note)
            ClockAttemptResult.Committed
        } catch (e: Exception) {
            ClockAttemptResult.Failed(
                "Your shift was recorded on this device, but the verified time couldn't reach the server: ${e.message}",
            )
        }
    }

    private fun formatDistance(metres: Double): String =
        if (metres >= 1000) "%.1f km".format(metres / 1000) else "${metres.roundToInt()} m"
}
