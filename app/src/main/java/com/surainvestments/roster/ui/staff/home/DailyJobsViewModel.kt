package com.surainvestments.roster.ui.staff.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.DailyJobRepository
import com.surainvestments.roster.data.repository.ShiftRepository
import com.surainvestments.roster.domain.model.DailyJobAssignment
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.dailyJobOrderComparator
import com.surainvestments.roster.domain.model.excludingOrphaned
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DailyJobsUiState(
    val isLoading: Boolean = true,
    val jobs: List<DailyJobAssignment> = emptyList(),
)

/**
 * Today's Daily Job assignments for the Home bell panel — completion toggling only, no
 * template-library access (staff have zero read access to `daily_job_templates`). Sort is by the
 * manager's drag-arranged `order` (mirrors iOS `sortedByOrder`) and **stable across complete/undo**
 * (`IOS-STAFF-AUDIT.md` §8): `order` is only ever changed by an explicit manager reorder, never by
 * a completion toggle, so the list never shifts purely from a tap — a row jumping after a tap would
 * read as a failed tap and invite mis-taps.
 */
@HiltViewModel
class DailyJobsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dailyJobRepository: DailyJobRepository,
    private val shiftRepository: ShiftRepository,
) : ViewModel() {

    private val todayKey = RosterCalendar.todayKey()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DailyJobsUiState> = authRepository.authStateFlow()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(DailyJobsUiState(isLoading = false))
            } else {
                combine(
                    dailyJobRepository.todaysAssignments(uid, todayKey),
                    shiftRepository.staffShiftsById(uid),
                ) { jobs, shiftsById ->
                    // Same self-heal as ClockInViewModel's orphaned-ClockSession handling: a
                    // deleted-then-recreated shift leaves its old assignment behind with no
                    // cascade-delete tying the two together.
                    val liveJobs = jobs.excludingOrphaned(validShiftIds = shiftsById.keys)
                    DailyJobsUiState(isLoading = false, jobs = liveJobs.sortedWith(dailyJobOrderComparator))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyJobsUiState())

    fun toggle(assignment: DailyJobAssignment) {
        val uid = authRepository.currentUid() ?: return
        val siblings = uiState.value.jobs
        viewModelScope.launch {
            runCatching { dailyJobRepository.setCompleted(assignment, uid, !assignment.completed, siblings) }
        }
    }
}
