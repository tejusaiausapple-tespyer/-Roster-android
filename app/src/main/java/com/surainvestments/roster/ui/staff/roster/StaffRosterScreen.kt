package com.surainvestments.roster.ui.staff.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.StaffShiftDisplayStatus
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.ui.components.EmptyState
import com.surainvestments.roster.ui.components.MiniStat
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.SectionHeader
import com.surainvestments.roster.ui.components.SoftTag
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.components.WeekSelector
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.staff.shared.rememberAddToCalendarAction
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors
import kotlinx.coroutines.launch

private data class SheetTarget(val shift: Shift, val timesheet: Timesheet?)

/** Android analogue of iOS's staff `RosterView` — a week-at-a-time view of the signed-in staff member's own shifts. */
@Composable
fun StaffRosterScreen(
    viewModel: StaffRosterViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val navBarPadding = LocalNavBarPadding.current
    val scope = rememberCoroutineScope()

    var submitTarget by remember { mutableStateOf<SheetTarget?>(null) }
    var absentTarget by remember { mutableStateOf<SheetTarget?>(null) }
    var undoTarget by remember { mutableStateOf<Timesheet?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val addToCalendar = rememberAddToCalendarAction()

    submitTarget?.let { target ->
        SubmitHoursSheet(
            shift = target.shift,
            existing = target.timesheet,
            onDismiss = { submitTarget = null },
            onSubmitted = { submitTarget = null },
        )
        return
    }
    absentTarget?.let { target ->
        ReportAbsenceSheet(
            shift = target.shift,
            existing = target.timesheet,
            onDismiss = { absentTarget = null },
            onReported = { absentTarget = null },
        )
        return
    }
    if (showHistory) {
        StaffHistoryScreen(onBack = { showHistory = false })
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(top = ScreenPillTopBarHeight),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandIndigoStrong)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = ScreenPillTopBarHeight + 4.dp,
                    bottom = navBarPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    RosterHeader(
                        state = state,
                        onPrev = viewModel::onPrevWeek,
                        onNext = viewModel::onNextWeek,
                        onToday = viewModel::onToday,
                        onSelectDay = viewModel::onSelectDay,
                        onViewHistory = { showHistory = true },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (state.actionNeeded.isNotEmpty()) {
                    item {
                        ActionNeededSection(
                            items = state.actionNeeded,
                            onTap = { action -> submitTarget = SheetTarget(action.shift, action.timesheet) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                items(state.dayGroups, key = { it.dateKey }) { group ->
                    Column {
                        SectionHeader(
                            title = if (group.isToday) "${group.headerLabel} · Today" else group.headerLabel,
                        )
                        if (group.shifts.isEmpty()) {
                            EmptyDayRow()
                        } else {
                            RosterCard(contentPadding = 0.dp) {
                                group.shifts.forEachIndexed { index, shift ->
                                    ShiftRowWithActions(
                                        shift = shift,
                                        onSubmit = { submitTarget = SheetTarget(shift.shift, shift.timesheet) },
                                        onAbsence = { absentTarget = SheetTarget(shift.shift, shift.timesheet) },
                                        onUndo = { shift.timesheet?.let { undoTarget = it } },
                                        onAddToCalendar = { addToCalendar(shift.shift) },
                                    )
                                    if (index < group.shifts.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.dayGroups.all { it.shifts.isEmpty() } && state.actionNeeded.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.EventBusy,
                            title = "No shifts this week",
                            message = "Try another week, or check back once your roster is published.",
                        )
                    }
                }
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "Roster",
            icon = Icons.Filled.CalendarMonth,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    undoTarget?.let { timesheet ->
        AlertDialog(
            onDismissRequest = { undoTarget = null },
            title = { Text("Undo absence report?") },
            text = { Text("This removes your absence report so you can submit hours instead.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { viewModel.undoAbsence(timesheet.id) }
                    undoTarget = null
                }) { Text("Undo absence", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { undoTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RosterHeader(
    state: RosterUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelectDay: (String) -> Unit,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RosterCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MiniStat(value = "${state.stats.shiftsCount}", label = "Shifts", modifier = Modifier.weight(1f))
                    MiniStat(value = state.stats.hoursLabel, label = "Hours", modifier = Modifier.weight(1f))
                    MiniStat(
                        value = "${state.stats.toDoCount}",
                        label = "To do",
                        tint = if (state.stats.toDoCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
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
        RosterCard(onClick = onViewHistory) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = BrandIndigoStrong)
                Text(
                    text = "View Shift History",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionNeededSection(items: List<ActionNeededUi>, onTap: (ActionNeededUi) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Needs your attention",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.tertiary,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.shift.id }) { action ->
                RosterCard(onClick = { onTap(action) }, modifier = Modifier.width(170.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(action.dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            action.timeRange,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val tint = if (action.isRejected) MaterialTheme.colorScheme.error else BrandIndigoStrong
                        SoftTag(text = if (action.isRejected) "Resubmit hours" else "Submit hours", tint = tint)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDayRow() {
    RosterCard {
        Text(
            text = "No shift",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun statusTint(status: StaffShiftDisplayStatus) = when (status) {
    StaffShiftDisplayStatus.Scheduled -> StatusColors.Scheduled
    StaffShiftDisplayStatus.AwaitingSubmission -> StatusColors.Pending
    StaffShiftDisplayStatus.Draft -> StatusColors.Draft
    StaffShiftDisplayStatus.Pending -> StatusColors.Pending
    StaffShiftDisplayStatus.Approved -> StatusColors.Approved
    StaffShiftDisplayStatus.Rejected -> StatusColors.Rejected
    StaffShiftDisplayStatus.AbsentReported -> StatusColors.Absent
    StaffShiftDisplayStatus.Absent -> StatusColors.AbsentConfirmed
}

@Composable
private fun ShiftRowWithActions(
    shift: ShiftRowUi,
    onSubmit: () -> Unit,
    onAbsence: () -> Unit,
    onUndo: () -> Unit,
    onAddToCalendar: () -> Unit,
) {
    val tint = statusTint(shift.status)
    val canSubmit = BusinessRules.canSubmitHours(shift.shift, shift.timesheet)
    val canAbsence = BusinessRules.canReportAbsence(shift.shift, shift.timesheet)
    val canUndo = shift.timesheet?.isStaffReportedAbsence ?: false

    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(tint, shape = CircleShape),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = shift.timeRange,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                val subtitle = listOfNotNull(
                    shift.department?.takeIf { it.isNotBlank() },
                    shift.location?.takeIf { it.isNotBlank() },
                ).joinToString(" • ")
                if (subtitle.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SoftTag(text = shift.status.title, tint = tint)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canSubmit) {
                RowActionChip(
                    text = if (shift.timesheet?.status?.rawValue == "rejected") "Resubmit" else if (shift.timesheet != null) "Edit" else "Submit",
                    tint = BrandIndigoStrong,
                    onClick = onSubmit,
                )
            }
            if (canAbsence) {
                RowActionChip(text = "Absent", tint = MaterialTheme.colorScheme.tertiary, onClick = onAbsence)
            }
            if (canUndo) {
                RowActionChip(text = "Undo", tint = MaterialTheme.colorScheme.onSurfaceVariant, onClick = onUndo)
            }
            RowActionChip(text = "Calendar", tint = MaterialTheme.colorScheme.onSurfaceVariant, onClick = onAddToCalendar)
        }
    }
}

@Composable
private fun RowActionChip(text: String, tint: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = tint,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
