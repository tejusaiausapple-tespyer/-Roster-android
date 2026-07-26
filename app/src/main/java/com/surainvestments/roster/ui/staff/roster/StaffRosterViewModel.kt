package com.surainvestments.roster.ui.staff.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.ShiftRepository
import com.surainvestments.roster.data.repository.TimesheetRepository
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.StaffShiftDisplayStatus
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.domain.model.TimesheetStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShiftRowUi(
    val shift: Shift,
    val timesheet: Timesheet?,
    val timeRange: String,
    val location: String?,
    val department: String?,
    val status: StaffShiftDisplayStatus,
)

data class DayGroupUi(
    val dateKey: String,
    val headerLabel: String,
    val isToday: Boolean,
    val shifts: List<ShiftRowUi>,
)

data class ActionNeededUi(
    val shift: Shift,
    val timesheet: Timesheet?,
    val dateLabel: String,
    val timeRange: String,
    val isRejected: Boolean,
)

data class WeekStatsUi(
    val shiftsCount: Int = 0,
    val hoursLabel: String = "0",
    val toDoCount: Int = 0,
)

data class RosterUiState(
    val isLoading: Boolean = true,
    val mondayKey: String = RosterCalendar.weekStartKey(),
    val selectedDayKey: String = RosterCalendar.todayKey(),
    val canGoPrevWeek: Boolean = true,
    val canGoNextWeek: Boolean = true,
    val markedKeys: Set<String> = emptySet(),
    val stats: WeekStatsUi = WeekStatsUi(),
    val actionNeeded: List<ActionNeededUi> = emptyList(),
    val dayGroups: List<DayGroupUi> = emptyList(),
)

/**
 * Staff-facing "my shifts" — a week-at-a-time view over the -28/+56 day window
 * ([BusinessRules.shiftWindowDaysBack]/[BusinessRules.shiftWindowDaysForward]), matching iOS
 * `RosterView`. Status uses [BusinessRules.displayStatus] — the staff-facing status type,
 * distinct from the manager dashboard's `ManagerShiftStatus`.
 */
@HiltViewModel
class StaffRosterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shiftRepository: ShiftRepository,
    private val timesheetRepository: TimesheetRepository,
) : ViewModel() {

    private val weekOffsetFlow = MutableStateFlow(0)
    private val selectedDayKeyFlow = MutableStateFlow(RosterCalendar.todayKey())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RosterUiState> = authRepository.authStateFlow()
        .flatMapLatest { staffId ->
            if (staffId == null) {
                flowOf(RosterUiState(isLoading = false))
            } else {
                // Shared, cached flows — Home/Roster/Tasks all read the same underlying
                // Firestore listeners instead of each opening their own (see ShiftRepository).
                combine(
                    shiftRepository.staffShiftsWindow(staffId),
                    timesheetRepository.staffTimesheetsByShiftId(staffId),
                    weekOffsetFlow,
                    selectedDayKeyFlow,
                ) { shifts, timesheetsByShiftId, weekOffset, selectedDayKey ->
                    buildUiState(shifts, timesheetsByShiftId, weekOffset, selectedDayKey)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RosterUiState())

    fun onPrevWeek() {
        val bounds = BusinessRules.shiftWeekOffsetBounds()
        weekOffsetFlow.value = (weekOffsetFlow.value - 1).coerceIn(bounds.first, bounds.last)
    }

    fun onNextWeek() {
        val bounds = BusinessRules.shiftWeekOffsetBounds()
        weekOffsetFlow.value = (weekOffsetFlow.value + 1).coerceIn(bounds.first, bounds.last)
    }

    fun onToday() {
        weekOffsetFlow.value = 0
        selectedDayKeyFlow.value = RosterCalendar.todayKey()
    }

    fun onSelectDay(dateKey: String) {
        selectedDayKeyFlow.value = dateKey
    }

    /** Looks up a shift (+ its timesheet, if any) already held in the shared cached window — for opening Submit Hours from a notification deep link. */
    fun findShift(shiftId: String): Pair<Shift, Timesheet?>? {
        val uid = authRepository.currentUid() ?: return null
        val shift = shiftRepository.staffShiftsById(uid).value[shiftId] ?: return null
        return shift to timesheetRepository.staffTimesheetsByShiftId(uid).value[shiftId]
    }

    /** Deletes a self-reported absence so the staff member can submit hours instead. */
    fun undoAbsence(timesheetId: String) {
        viewModelScope.launch {
            runCatching { timesheetRepository.undoAbsenceReport(timesheetId) }
        }
    }

    private fun buildUiState(
        shifts: List<Shift>,
        timesheetsByShiftId: Map<String, Timesheet>,
        weekOffset: Int,
        selectedDayKey: String,
    ): RosterUiState {
        val now = Instant.now()
        val bounds = BusinessRules.shiftWeekOffsetBounds(now)
        val clampedOffset = weekOffset.coerceIn(bounds.first, bounds.last)
        val mondayKey = RosterCalendar.addWeeksToKey(clampedOffset, RosterCalendar.weekStartKey(now))
        val weekDayKeys = RosterCalendar.weekDayKeys(mondayKey)
        val weekKeySet = weekDayKeys.toSet()
        val todayKey = RosterCalendar.todayKey(now)

        val weekShifts = shifts
            .filter { it.date in weekKeySet }
            .sortedWith(compareBy({ it.date }, { it.rosteredStart }))

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

        val dayGroups = weekDayKeys.map { dateKey ->
            DayGroupUi(
                dateKey = dateKey,
                headerLabel = RosterFormat.dayHeader(dateKey),
                isToday = dateKey == todayKey,
                shifts = weekShifts.filter { it.date == dateKey }.map(::toRow),
            )
        }

        val actionNeeded = weekShifts
            .filter { BusinessRules.needsStaffAction(it, timesheetsByShiftId[it.id], now) }
            .map { shift ->
                val timesheet = timesheetsByShiftId[shift.id]
                ActionNeededUi(
                    shift = shift,
                    timesheet = timesheet,
                    dateLabel = RosterFormat.dateShort(shift.date),
                    timeRange = "${RosterFormat.timeOfDay(shift.rosteredStart)}–" +
                        RosterFormat.timeOfDay(shift.rosteredEnd),
                    isRejected = timesheet?.status == TimesheetStatus.Rejected,
                )
            }

        return RosterUiState(
            isLoading = false,
            mondayKey = mondayKey,
            selectedDayKey = selectedDayKey,
            canGoPrevWeek = clampedOffset > bounds.first,
            canGoNextWeek = clampedOffset < bounds.last,
            markedKeys = weekShifts.map { it.date }.toSet(),
            stats = WeekStatsUi(
                shiftsCount = weekShifts.size,
                hoursLabel = RosterFormat.decimalHours(weekShifts.sumOf { it.scheduledHours }),
                toDoCount = actionNeeded.size,
            ),
            actionNeeded = actionNeeded,
            dayGroups = dayGroups,
        )
    }
}
