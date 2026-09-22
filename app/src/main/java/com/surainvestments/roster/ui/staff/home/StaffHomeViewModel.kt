package com.surainvestments.roster.ui.staff.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AppSettingsRepository
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.DailyJobRepository
import com.surainvestments.roster.data.repository.RosterTaskRepository
import com.surainvestments.roster.data.repository.ShiftRepository
import com.surainvestments.roster.data.repository.TaskCompletionRepository
import com.surainvestments.roster.data.repository.TimesheetRepository
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.AppSettings
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.DailyJobAssignment
import com.surainvestments.roster.domain.model.HoursMetrics
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.RosterTask
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.TaskCompletion
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.domain.model.TimesheetStatus
import com.surainvestments.roster.domain.model.excludingOrphaned
import com.surainvestments.roster.ui.staff.roster.ShiftRowUi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZonedDateTime
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

data class DailyJobsSnapshotUi(
    val totalCount: Int = 0,
    val doneCount: Int = 0,
    val pendingCount: Int = 0,
)

data class TasksSnapshotUi(
    val totalCount: Int = 0,
    val doneCount: Int = 0,
    val pendingCount: Int = 0,
)

data class PendingTimesheetUi(
    val shift: Shift,
    val timesheet: Timesheet?,
    val dateKey: String,
    val dateLabel: String,
    val timeRangeLabel: String,
    val location: String?,
    val isRejected: Boolean = false,
)

data class TimesheetsSnapshotUi(
    val pendingItems: List<PendingTimesheetUi> = emptyList(),
) {
    val pendingCount: Int get() = pendingItems.size
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val companyName: String = "SURA INVESTMENT'S PTY LTD",
    val greetingName: String = "",
    val formattedDate: String = "",
    val todaysShifts: List<ShiftRowUi> = emptyList(),
    val dailyJobsSnapshot: DailyJobsSnapshotUi = DailyJobsSnapshotUi(),
    val tasksSnapshot: TasksSnapshotUi = TasksSnapshotUi(),
    val timesheetsSnapshot: TimesheetsSnapshotUi = TimesheetsSnapshotUi(),
    val metrics: HoursMetrics = HoursMetrics(),
    val upcomingShifts: List<UpcomingShiftUi> = emptyList(),
)

private data class HomeDataA(
    val user: AppUser?,
    val settings: AppSettings,
    val shifts: List<Shift>,
    val timesheets: Map<String, Timesheet>,
)

private data class HomeDataB(
    val dailyJobs: List<DailyJobAssignment>,
    val tasks: List<RosterTask>,
    val completions: List<TaskCompletion>,
)

/** Android analogue of iOS's staff `HomeView` — header pill, greeting, today's shift, daily jobs, tasks, hours snapshot, timesheets, up next. */
@HiltViewModel
class StaffHomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    shiftRepository: ShiftRepository,
    timesheetRepository: TimesheetRepository,
    dailyJobRepository: DailyJobRepository,
    rosterTaskRepository: RosterTaskRepository,
    taskCompletionRepository: TaskCompletionRepository,
    appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val todayKey = RosterCalendar.todayKey()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = authRepository.authStateFlow()
        .flatMapLatest { staffId ->
            if (staffId == null) {
                flowOf(HomeUiState(isLoading = false))
            } else {
                val now = Instant.now()
                val mondayKey = RosterCalendar.weekStartKey(now)
                val weekDayKeys = RosterCalendar.weekDayKeys(mondayKey)

                val flowA = combine(
                    authRepository.userProfileFlow(staffId),
                    appSettingsRepository.appSettings,
                    shiftRepository.staffShiftsWindow(staffId),
                    timesheetRepository.staffTimesheetsByShiftId(staffId),
                ) { user, settings, shifts, timesheets ->
                    HomeDataA(user, settings, shifts, timesheets)
                }

                val flowB = combine(
                    dailyJobRepository.todaysAssignments(staffId, todayKey),
                    rosterTaskRepository.tasks,
                    taskCompletionRepository.sharedCompletionsForWeek(mondayKey, weekDayKeys),
                ) { dailyJobs, tasks, completions ->
                    HomeDataB(dailyJobs, tasks, completions)
                }

                combine(flowA, flowB) { a, b ->
                    buildUiState(staffId, a.user, a.settings, a.shifts, a.timesheets, b.dailyJobs, b.tasks, b.completions)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun buildUiState(
        staffId: String,
        user: AppUser?,
        settings: AppSettings,
        shifts: List<Shift>,
        timesheetsByShiftId: Map<String, Timesheet>,
        dailyJobs: List<DailyJobAssignment>,
        tasks: List<RosterTask>,
        completions: List<TaskCompletion>,
    ): HomeUiState {
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

        // Company Name
        val companyName = settings.companyName.ifBlank { "SURA INVESTMENT'S PTY LTD" }

        // Greeting
        val firstName = user?.fullName
            ?.split(' ')
            ?.firstOrNull { it.isNotBlank() }
            ?: "there"
        val hour = ZonedDateTime.ofInstant(now, RosterCalendar.zoneId).hour
        val greetingPrefix = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val greetingName = "$greetingPrefix $firstName"

        // Daily Jobs snapshot
        val liveDailyJobs = dailyJobs.excludingOrphaned(validShiftIds = shifts.map { it.id }.toSet())
        val jobsTotal = liveDailyJobs.size
        val jobsDone = liveDailyJobs.count { it.completed }
        val jobsPending = liveDailyJobs.count { !it.completed }

        // Tasks snapshot
        val selectedWeekday = RosterCalendar.weekdayForKey(todayKey)
        val completionsByTaskId = completions.filter { it.date == todayKey }.associateBy { it.taskId }
        val myTodayTasks = tasks.filter { it.isAssigned(staffId) && it.isActive(onDayKey = todayKey, weekday = selectedWeekday) }
        val tasksTotal = myTodayTasks.size
        val tasksDone = myTodayTasks.count { completionsByTaskId[it.id]?.completed == true }
        val tasksPending = myTodayTasks.count { completionsByTaskId[it.id]?.completed != true }

        // Timesheets snapshot
        val pendingTimesheetItems = sorted
            .filter { shift ->
                val ts = timesheetsByShiftId[shift.id]
                if (ts?.status == TimesheetStatus.Approved) return@filter false
                if (ts?.status == TimesheetStatus.Rejected) return@filter true
                if (shift.date < todayKey && ts == null) return@filter true
                if (shift.date == todayKey && ts == null && shift.isSubmittable(now)) return@filter true
                false
            }
            .map { shift ->
                val ts = timesheetsByShiftId[shift.id]
                PendingTimesheetUi(
                    shift = shift,
                    timesheet = ts,
                    dateKey = shift.date,
                    dateLabel = RosterFormat.dayHeader(shift.date),
                    timeRangeLabel = "${RosterFormat.timeOfDay(shift.rosteredStart)} – ${RosterFormat.timeOfDay(shift.rosteredEnd)}",
                    location = shift.location,
                    isRejected = ts?.status == TimesheetStatus.Rejected,
                )
            }

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

        return HomeUiState(
            isLoading = false,
            companyName = companyName,
            greetingName = greetingName,
            formattedDate = RosterFormat.dateFull(now),
            todaysShifts = todaysShifts,
            dailyJobsSnapshot = DailyJobsSnapshotUi(totalCount = jobsTotal, doneCount = jobsDone, pendingCount = jobsPending),
            tasksSnapshot = TasksSnapshotUi(totalCount = tasksTotal, doneCount = tasksDone, pendingCount = tasksPending),
            timesheetsSnapshot = TimesheetsSnapshotUi(pendingItems = pendingTimesheetItems),
            metrics = HoursMetrics.compute(timesheetsByShiftId.values.toList(), shifts, now),
            upcomingShifts = upcomingShifts,
        )
    }
}
