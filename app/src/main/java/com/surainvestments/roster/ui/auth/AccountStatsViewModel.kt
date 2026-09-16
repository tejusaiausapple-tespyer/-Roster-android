package com.surainvestments.roster.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.TimesheetRepository
import com.surainvestments.roster.domain.model.TimesheetStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AccountStatsUiState(
    val approvedHours: Double = 0.0,
    val timesheetCount: Int = 0,
    val pendingCount: Int = 0,
)

/**
 * Backs the Account tab's mini-stat row (`StaffStatsSection`) — approved hours (all-time,
 * matching [com.surainvestments.roster.domain.model.HoursMetrics.all]'s definition), total
 * timesheet count, and pending count. Reads the same shared, cached
 * [TimesheetRepository.staffTimesheetsByShiftId] listener Home/Roster already subscribe to, so
 * this adds no new Firestore reads.
 */
@HiltViewModel
class AccountStatsViewModel @Inject constructor(
    authRepository: AuthRepository,
    timesheetRepository: TimesheetRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AccountStatsUiState> = authRepository.authStateFlow()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(AccountStatsUiState())
            } else {
                timesheetRepository.staffTimesheetsByShiftId(uid).map { byShiftId ->
                    val timesheets = byShiftId.values
                    AccountStatsUiState(
                        approvedHours = timesheets
                            .filter { it.status == TimesheetStatus.Approved }
                            .sumOf { it.workedHours },
                        timesheetCount = timesheets.size,
                        pendingCount = timesheets.count { it.status == TimesheetStatus.Pending },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountStatsUiState())
}
