package com.surainvestments.roster.ui.manager.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AppSettingsRepository
import com.surainvestments.roster.data.repository.DailyJobRepository
import com.surainvestments.roster.data.repository.RosterTaskRepository
import com.surainvestments.roster.data.repository.ShiftAttendanceRepository
import com.surainvestments.roster.data.repository.ShiftRepository
import com.surainvestments.roster.data.repository.StaffRepository
import com.surainvestments.roster.data.repository.TaskCompletionRepository
import com.surainvestments.roster.data.repository.TimesheetRepository
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.DailyJobAssignment
import com.surainvestments.roster.domain.model.ManagerShiftStatus
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.RosterTask
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.ShiftAttendance
import com.surainvestments.roster.domain.model.TaskCompletion
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.domain.model.TimesheetStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RosterRowUi(
    val shiftId: String,
    val name: String,
    val role: String,
    val timeRange: String,
    val status: ManagerShiftStatus,
    val jobsDone: Int,
    val jobsTotal: Int,
)

data class TaskLogRowUi(
    val id: String,
    val taskTitle: String,
    val staffName: String,
    val timeLabel: String,
    val verified: Boolean,
    val hasPhoto: Boolean,
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val companyName: String = "Rosterra",
    val formattedDate: String = "",
    val activeStaffCount: Int = 0,
    val totalShiftsCount: Int = 0,
    val totalScheduledHours: Double = 0.0,
    val completedTasksCount: Int = 0,
    val totalTasksCount: Int = 0,
    val pendingTimesheetsCount: Int = 0,
    val rosterRows: List<RosterRowUi> = emptyList(),
    val taskLogRows: List<TaskLogRowUi> = emptyList(),
)

/** Android analogue of iOS's `ManagerDashboardView` computed properties, backed by live Firestore flows. */
@HiltViewModel
class ManagerDashboardViewModel @Inject constructor(
    shiftRepository: ShiftRepository,
    timesheetRepository: TimesheetRepository,
    rosterTaskRepository: RosterTaskRepository,
    taskCompletionRepository: TaskCompletionRepository,
    shiftAttendanceRepository: ShiftAttendanceRepository,
    dailyJobRepository: DailyJobRepository,
    appSettingsRepository: AppSettingsRepository,
    staffRepository: StaffRepository,
) : ViewModel() {

    // Fixed for the ViewModel's lifetime — matches iOS's own per-render RosterCalendar.todayKey()
    // closely enough for a foregrounded dashboard; a screen left open across midnight would need
    // a periodic re-check, out of scope for v1.
    private val todayKey = RosterCalendar.todayKey()
    private val weekday = RosterCalendar.weekday()

    val uiState: StateFlow<DashboardUiState> = combine(
        shiftRepository.shiftsForDate(todayKey),
        timesheetRepository.recentTimesheets(),
        rosterTaskRepository.activeTasks(),
        taskCompletionRepository.completionsForDate(todayKey),
        shiftAttendanceRepository.attendanceForDate(todayKey),
    ) { shifts, timesheets, tasks, completions, attendance ->
        RawSlice(shifts, timesheets, tasks, completions, attendance)
    }.combine(dailyJobRepository.assignmentsForDate(todayKey)) { slice, jobs ->
        slice to jobs
    }.combine(appSettingsRepository.appSettingsFlow()) { (slice, jobs), settings ->
        Triple(slice, jobs, settings)
    }.combine(staffRepository.staffListFlow()) { (slice, jobs, settings), staff ->
        buildUiState(slice, jobs, settings.companyName, staff)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun buildUiState(
        slice: RawSlice,
        jobs: List<DailyJobAssignment>,
        companyName: String,
        staff: List<AppUser>,
    ): DashboardUiState {
        val staffById = staff.associateBy { it.id }
        val timesheetsByShiftId = slice.timesheets.associateBy { it.shiftId }
        val attendanceByShiftId = slice.attendance.associateBy { it.shiftId }
        val now = Instant.now()

        val todaysShifts = slice.shifts.sortedBy { it.rosteredStart }
        val activeStaffCount = todaysShifts.count { timesheetsByShiftId.containsKey(it.id) }
        val totalScheduledHours = todaysShifts.sumOf { it.scheduledHours }

        val todaysTasks = slice.tasks.filter { it.isActive(onDayKey = todayKey, weekday = weekday) }
        val completedTasksCount = todaysTasks.count { task ->
            slice.completions.any { it.taskId == task.id && it.completed }
        }

        val pendingTimesheetsCount = slice.timesheets.count { it.status == TimesheetStatus.Pending }

        val rosterRows = todaysShifts.map { shift ->
            val timesheet = timesheetsByShiftId[shift.id]
            val attendance = attendanceByShiftId[shift.id]
            val status = BusinessRules.managerShiftStatus(shift, timesheet, attendance, now)
            val shiftJobs = jobs.filter { it.shiftId == shift.id }
            RosterRowUi(
                shiftId = shift.id,
                name = staffById[shift.staffId]?.fullName ?: "Staff Member",
                role = shift.department?.takeIf { it.isNotBlank() } ?: "General",
                timeRange = "${shift.rosteredStart} - ${shift.rosteredEnd}",
                status = status,
                jobsDone = shiftJobs.count { it.completed },
                jobsTotal = shiftJobs.size,
            )
        }

        val taskLogRows = slice.completions
            .sortedByDescending { it.completedAt ?: Instant.EPOCH }
            .map { completion ->
                val task = slice.tasks.firstOrNull { it.id == completion.taskId }
                val staffMember = staffById[completion.completedBy]
                TaskLogRowUi(
                    id = completion.id,
                    taskTitle = task?.title ?: "Task Completed",
                    staffName = staffMember?.fullName ?: "Staff",
                    timeLabel = completion.completedAt?.let { RosterFormat.time(it) } ?: "—",
                    verified = completion.completed,
                    hasPhoto = !completion.staffPhotoUrl.isNullOrEmpty(),
                )
            }

        return DashboardUiState(
            isLoading = false,
            companyName = companyName,
            formattedDate = RosterFormat.dateFull(now),
            activeStaffCount = activeStaffCount,
            totalShiftsCount = todaysShifts.size,
            totalScheduledHours = totalScheduledHours,
            completedTasksCount = completedTasksCount,
            totalTasksCount = todaysTasks.size,
            pendingTimesheetsCount = pendingTimesheetsCount,
            rosterRows = rosterRows,
            taskLogRows = taskLogRows,
        )
    }

    private data class RawSlice(
        val shifts: List<Shift>,
        val timesheets: List<Timesheet>,
        val tasks: List<RosterTask>,
        val completions: List<TaskCompletion>,
        val attendance: List<ShiftAttendance>,
    )
}
