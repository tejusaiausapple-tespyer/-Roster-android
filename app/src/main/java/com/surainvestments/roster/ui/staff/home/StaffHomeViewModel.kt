package com.surainvestments.roster.ui.staff.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.ShiftRepository
import com.surainvestments.roster.data.repository.TimesheetRepository
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.HoursMetrics
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.ui.staff.roster.ShiftRowUi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class UpcomingShiftUi(
    val dateKey: String,
    val dayLabel: String,
    val shift: ShiftRowUi,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val greetingName: String = "",
    val formattedDate: String = "",
    val todaysShifts: List<ShiftRowUi> = emptyList(),
    val upcomingShifts: List<UpcomingShiftUi> = emptyList(),
    val metrics: HoursMetrics = HoursMetrics(),
)

/** Android analogue of iOS's staff `HomeView` — today's shift + a short look-ahead. */
@HiltViewModel
class StaffHomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    shiftRepository: ShiftRepository,
    timesheetRepository: TimesheetRepository,
) : ViewModel() {

    private val todayKey = RosterCalendar.todayKey()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = authRepository.authStateFlow()
        .flatMapLatest { staffId ->
            if (staffId == null) {
                flowOf(HomeUiState(isLoading = false))
            } else {
                // Shared, cached flows — same underlying listeners StaffRosterViewModel reads.
                combine(
                    authRepository.userProfileFlow(staffId),
                    shiftRepository.staffShiftsWindow(staffId),
                    timesheetRepository.staffTimesheetsByShiftId(staffId),
                ) { user, shifts, timesheetsByShiftId -> buildUiState(user, shifts, timesheetsByShiftId) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun buildUiState(user: AppUser?, shifts: List<Shift>, timesheetsByShiftId: Map<String, Timesheet>): HomeUiState {
        val now = Instant.now()
        val sorted = shifts.sortedWith(compareBy({ it.date }, { it.rosteredStart }))

        fun toRow(shift: Shift): ShiftRowUi {
            val timesheet = timesheetsByShiftId[shift.id]
            return ShiftRowUi(
                shift = shift,
                timesheet = timesheet,
                timeRange = "${RosterFormat.timeOfDay(shift.rosteredStart)} – " +
                    RosterFormat.timeOfDay(shift.rosteredEnd),
                location = shift.location,
                department = shift.department,
                status = BusinessRules.displayStatus(shift = shift, timesheet = timesheet, now = now),
            )
        }

        val todaysShifts = sorted.filter { it.date == todayKey }.map(::toRow)
        val upcomingShifts = sorted
            .filter { it.date > todayKey }
            .take(5)
            .map { shift ->
                UpcomingShiftUi(
                    dateKey = shift.date,
                    dayLabel = RosterFormat.dayHeader(shift.date),
                    shift = toRow(shift),
                )
            }

        val firstName = user?.fullName
            ?.split(' ')
            ?.firstOrNull { it.isNotBlank() }
            ?: "there"

        return HomeUiState(
            isLoading = false,
            greetingName = firstName,
            formattedDate = RosterFormat.dateFull(now),
            todaysShifts = todaysShifts,
            upcomingShifts = upcomingShifts,
            metrics = HoursMetrics.compute(timesheetsByShiftId.values.toList(), shifts, now),
        )
    }
}
