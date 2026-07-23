package com.surainvestments.roster.ui.staff.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.local.TaskPhotoCache
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.RosterTaskRepository
import com.surainvestments.roster.data.repository.TaskCompletionRepository
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterTask
import com.surainvestments.roster.domain.model.TaskCompletion
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

data class TaskRowUi(val task: RosterTask, val completion: TaskCompletion?, val isCompleted: Boolean)

data class TaskStatsUi(val total: Int = 0, val completed: Int = 0, val pending: Int = 0)

data class TasksUiState(
    val isLoading: Boolean = true,
    val mondayKey: String = RosterCalendar.weekStartKey(),
    val selectedDayKey: String = RosterCalendar.todayKey(),
    val canGoPrevWeek: Boolean = true,
    val canGoNextWeek: Boolean = true,
    val markedKeys: Set<String> = emptySet(),
    val stats: TaskStatsUi = TaskStatsUi(),
    val rows: List<TaskRowUi> = emptyList(),
)

/**
 * Staff-facing task list — today's (or any browsable day's) applicable tasks, filtered by
 * [RosterTask.isActive]/[RosterTask.isAssigned] and sorted incomplete-first, then priority, then
 * due time. Mirrors iOS `TasksView`.
 */
@HiltViewModel
class StaffTasksViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val rosterTaskRepository: RosterTaskRepository,
    private val taskCompletionRepository: TaskCompletionRepository,
    taskPhotoCache: TaskPhotoCache,
) : ViewModel() {

    private val weekOffsetFlow = MutableStateFlow(0)
    private val selectedDayKeyFlow = MutableStateFlow(RosterCalendar.todayKey())

    init {
        // Once per session, matching iOS's own "no background sync" precedent (see TaskPhotoCache doc).
        taskPhotoCache.removePhotosBeforeCurrentWeek()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TasksUiState> = authRepository.authStateFlow()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(TasksUiState(isLoading = false))
            } else {
                combine(weekOffsetFlow, selectedDayKeyFlow) { offset, day -> offset to day }
                    .flatMapLatest { (weekOffset, selectedDayKey) ->
                        val now = Instant.now()
                        val mondayKey = RosterCalendar.addWeeksToKey(weekOffset, RosterCalendar.weekStartKey(now))
                        val weekDayKeys = RosterCalendar.weekDayKeys(mondayKey)
                        combine(
                            rosterTaskRepository.tasks,
                            taskCompletionRepository.sharedCompletionsForWeek(mondayKey, weekDayKeys),
                        ) { tasks, weekCompletions ->
                            build(uid, tasks, weekCompletions, weekOffset, selectedDayKey, mondayKey, now)
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

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

    private fun build(
        uid: String,
        tasks: List<RosterTask>,
        weekCompletions: List<TaskCompletion>,
        weekOffset: Int,
        selectedDayKey: String,
        mondayKey: String,
        now: Instant,
    ): TasksUiState {
        val bounds = BusinessRules.shiftWeekOffsetBounds(now)
        val clampedOffset = weekOffset.coerceIn(bounds.first, bounds.last)

        val myTasks = tasks.filter { it.isAssigned(uid) }
        val myTaskIds = myTasks.map { it.id }.toSet()
        val markedKeys = weekCompletions
            .filter { it.completed && myTaskIds.contains(it.taskId) }
            .map { it.date }
            .toSet()

        val selectedWeekday = RosterCalendar.weekdayForKey(selectedDayKey)
        val completionsByTaskId = weekCompletions
            .filter { it.date == selectedDayKey }
            .associateBy { it.taskId }

        val rows = myTasks
            .filter { it.isActive(onDayKey = selectedDayKey, weekday = selectedWeekday) }
            .map { task ->
                val completion = completionsByTaskId[task.id]
                TaskRowUi(task = task, completion = completion, isCompleted = completion?.completed == true)
            }
            .sortedWith(
                compareBy(
                    { it.isCompleted },
                    { it.task.priorityLevel.weight },
                    { it.task.dueTime ?: "99:99" },
                ),
            )

        return TasksUiState(
            isLoading = false,
            mondayKey = mondayKey,
            selectedDayKey = selectedDayKey,
            canGoPrevWeek = clampedOffset > bounds.first,
            canGoNextWeek = clampedOffset < bounds.last,
            markedKeys = markedKeys,
            stats = TaskStatsUi(
                total = rows.size,
                completed = rows.count { it.isCompleted },
                pending = rows.count { !it.isCompleted },
            ),
            rows = rows,
        )
    }
}
