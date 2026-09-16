package com.surainvestments.roster.ui.staff.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.TaskPriority
import com.surainvestments.roster.ui.components.EmptyState
import com.surainvestments.roster.ui.components.MiniStat
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenLoadingSkeleton
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.WeekSelector
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors

/** Android analogue of iOS's staff `TasksView` — today's (or any browsable day's) applicable tasks. */
@Composable
fun StaffTasksScreen(
    viewModel: StaffTasksViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val navBarPadding = LocalNavBarPadding.current
    var selectedTask by remember { mutableStateOf<TaskRowUi?>(null) }

    selectedTask?.let { row ->
        TaskCompletionDetailSheet(
            task = row.task,
            dateKey = state.selectedDayKey,
            completion = row.completion,
            onDismiss = { selectedTask = null },
        )
        return
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.isLoading -> ScreenLoadingSkeleton(modifier = Modifier.fillMaxSize())
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = ScreenPillTopBarHeight + 4.dp,
                    bottom = navBarPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    TasksHeader(
                        state = state,
                        onPrev = viewModel::onPrevWeek,
                        onNext = viewModel::onNextWeek,
                        onToday = viewModel::onToday,
                        onSelectDay = viewModel::onSelectDay,
                    )
                }
                if (state.rows.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Checklist,
                            title = "No Tasks Scheduled",
                            message = "Nothing due for this day.",
                        )
                    }
                } else {
                    items(state.rows, key = { it.task.id }) { row ->
                        TaskRow(row = row, onClick = { selectedTask = row })
                    }
                }
            }
        }

        ScreenPillTopBar(title = "Tasks", icon = Icons.Outlined.Checklist, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun TasksHeader(
    state: TasksUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelectDay: (String) -> Unit,
) {
    RosterCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniStat(value = "${state.stats.total}", label = "Tasks", modifier = Modifier.weight(1f))
                MiniStat(value = "${state.stats.completed}", label = "Completed", modifier = Modifier.weight(1f))
                MiniStat(
                    value = "${state.stats.pending}",
                    label = "Pending",
                    tint = if (state.stats.pending > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            WeekSelector(
                mondayKey = state.mondayKey,
                selectedKey = state.selectedDayKey,
                markedKeys = state.markedKeys,
                canGoPrev = state.canGoPrevWeek,
                canGoNext = state.canGoNextWeek,
                onPrev = onPrev,
                onNext = onNext,
                onToday = onToday,
                onSelect = onSelectDay,
            )
        }
    }
}

@Composable
private fun TaskRow(row: TaskRowUi, onClick: () -> Unit) {
    val accent = if (row.isCompleted) StatusColors.Approved else StatusColors.Pending
    RosterCard(accentColor = accent, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (row.task.priorityLevel == TaskPriority.High) {
                        Icon(
                            imageVector = Icons.Filled.PriorityHigh,
                            contentDescription = "High priority",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = row.task.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                if (row.task.dueTime != null && !row.isCompleted) {
                    IconLine(
                        icon = Icons.Filled.Schedule,
                        text = "Due by ${RosterFormat.timeOfDay(row.task.dueTime)}",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (row.completion?.isRedoRequested == true) {
                    IconLine(icon = Icons.Filled.Replay, text = "Redo requested", tint = MaterialTheme.colorScheme.error)
                }
                row.task.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val completedAt = row.completion?.completedAt
                if (row.isCompleted && completedAt != null) {
                    Text(
                        text = "Completed at ${RosterFormat.time(completedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = if (row.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (row.isCompleted) "Completed" else "Pending",
                tint = accent,
            )
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}
