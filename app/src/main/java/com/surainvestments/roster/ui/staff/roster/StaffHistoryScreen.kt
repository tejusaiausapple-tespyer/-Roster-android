package com.surainvestments.roster.ui.staff.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.domain.model.TimesheetStatus
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.EmptyState
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.StatTile
import com.surainvestments.roster.ui.components.StatusPill
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors

/** Full-screen "shift history" — the staff member's full 5-year submitted-hours record. Mirrors iOS `HistoryView`. */
@Composable
fun StaffHistoryScreen(
    onBack: () -> Unit,
    viewModel: StaffHistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val navBarPadding = LocalNavBarPadding.current
    var submitTarget by remember { mutableStateOf<Pair<Shift, Timesheet>?>(null) }

    submitTarget?.let { (shift, timesheet) ->
        SubmitHoursSheet(
            shift = shift,
            existing = timesheet,
            onDismiss = { submitTarget = null },
            onSubmitted = { submitTarget = null },
        )
        return
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterRow(
                            period = state.period,
                            statusFilter = state.statusFilter,
                            onPeriodChange = viewModel::setPeriod,
                            onStatusChange = viewModel::setStatusFilter,
                        )
                        OutlinedTextField(
                            value = state.search,
                            onValueChange = viewModel::setSearch,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search by location or date") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                        )
                        StatGrid(state.metrics)
                        if (state.metrics.pendingHours > 0) {
                            Banner(
                                kind = BannerKind.Warning,
                                title = "${RosterFormat.decimalHours(state.metrics.pendingHours)}h awaiting approval",
                                message = "${state.metrics.pendingCount} submission${if (state.metrics.pendingCount == 1) "" else "s"} pending review.",
                            )
                        }
                    }
                }
                if (state.isEmpty) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.EventBusy,
                            title = "Nothing here yet",
                            message = "Timesheets you submit will appear here.",
                        )
                    }
                } else {
                    state.groups.forEach { group ->
                        item(key = "header_${group.monthLabel}") {
                            Text(
                                text = group.monthLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        items(group.entries, key = { it.timesheet.id }) { entry ->
                            HistoryEntryRow(
                                entry = entry,
                                onResubmit = {
                                    entry.shift?.let { submitTarget = it to entry.timesheet }
                                },
                            )
                        }
                    }
                }
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "History",
            icon = Icons.Filled.History,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun StatGrid(metrics: com.surainvestments.roster.domain.model.HoursMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(RosterFormat.decimalHours(metrics.week), "This week", unit = "h", modifier = Modifier.weight(1f))
            StatTile(RosterFormat.decimalHours(metrics.month), "This month", unit = "h", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(RosterFormat.decimalHours(metrics.year), "This year", unit = "h", modifier = Modifier.weight(1f))
            StatTile(RosterFormat.decimalHours(metrics.all), "All time", unit = "h", modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    period: HistoryPeriod,
    statusFilter: HistoryStatusFilter,
    onPeriodChange: (HistoryPeriod) -> Unit,
    onStatusChange: (HistoryStatusFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var periodMenuOpen by remember { mutableStateOf(false) }
        Box {
            AssistChip(onClick = { periodMenuOpen = true }, label = { Text(period.label) })
            DropdownMenu(expanded = periodMenuOpen, onDismissRequest = { periodMenuOpen = false }) {
                HistoryPeriod.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { onPeriodChange(option); periodMenuOpen = false })
                }
            }
        }
        var statusMenuOpen by remember { mutableStateOf(false) }
        Box {
            AssistChip(onClick = { statusMenuOpen = true }, label = { Text(statusFilter.label) })
            DropdownMenu(expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                HistoryStatusFilter.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { onStatusChange(option); statusMenuOpen = false })
                }
            }
        }
    }
}

private fun statusTint(status: TimesheetStatus) = when (status) {
    TimesheetStatus.Draft -> StatusColors.Draft
    TimesheetStatus.Pending -> StatusColors.Pending
    TimesheetStatus.Approved -> StatusColors.Approved
    TimesheetStatus.Rejected -> StatusColors.Rejected
    TimesheetStatus.AbsentReported -> StatusColors.Absent
    TimesheetStatus.Absent -> StatusColors.AbsentConfirmed
}

@Composable
private fun HistoryEntryRow(entry: HistoryEntryUi, onResubmit: () -> Unit) {
    val ts = entry.timesheet
    RosterCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (entry.dateKey.isEmpty()) "Unknown date" else RosterFormat.dateShort(entry.dateKey),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    entry.shift?.location?.takeIf { it.isNotBlank() }?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StatusPill(label = ts.status.rawValue.replace('_', ' '), tint = statusTint(ts.status), compact = true)
            }

            if (ts.status != TimesheetStatus.Absent && ts.status != TimesheetStatus.AbsentReported) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    entry.shift?.let { shift ->
                        Metric(label = "ROSTERED", value = "${RosterFormat.timeOfDay(shift.rosteredStart)}–${RosterFormat.timeOfDay(shift.rosteredEnd)}")
                    }
                    if (ts.actualStart.isNotBlank()) {
                        Metric(label = "ACTUAL", value = "${RosterFormat.timeOfDay(ts.actualStart)}–${RosterFormat.timeOfDay(ts.actualEnd)}")
                    }
                    Metric(label = "WORKED", value = "${RosterFormat.decimalHours(ts.workedHours)}h")
                }
            }

            ts.staffNotes?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ts.status == TimesheetStatus.Rejected) {
                ts.rejectedReason?.takeIf { it.isNotBlank() }?.let {
                    Text(text = "Rejected: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (entry.shift != null) {
                    AssistChip(onClick = onResubmit, label = { Text("Resubmit") })
                }
            }
            ts.managerNotes?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
    }
}
