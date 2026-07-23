package com.surainvestments.roster.ui.manager.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.ManagerShiftStatus
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.components.SectionHeader
import com.surainvestments.roster.ui.components.SoftTag
import com.surainvestments.roster.ui.manager.staff.StaffListScreen
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.AccentEmeraldLight
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors
import com.surainvestments.roster.ui.theme.WarningAmberLight

/** Android analogue of iOS's `ManagerDashboardView`. */
@Composable
fun ManagerDashboardScreen(
    viewModel: ManagerDashboardViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    var showStaffDirectory by remember { mutableStateOf(false) }
    var comingSoonTitle by remember { mutableStateOf<String?>(null) }
    val state by viewModel.uiState.collectAsState()

    if (showStaffDirectory) {
        StaffListScreen(
            onBack = { showStaffDirectory = false },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LocalNavBarPadding.current)
                .padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(modifier = Modifier.height(ScreenPillTopBarHeight))

                HeaderCard(companyName = state.companyName, formattedDate = state.formattedDate)

                MetricsGrid(state)

                QuickActionsSection(
                    onNewShift = { comingSoonTitle = "New Shift" },
                    onNewTask = { comingSoonTitle = "New Task" },
                    onStaffDirectory = { showStaffDirectory = true },
                )

                ActiveRosterSection(rows = state.rosterRows)

                RecentTasksSection(rows = state.taskLogRows)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "Dashboard",
            icon = Icons.Filled.GridView,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    comingSoonTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { comingSoonTitle = null },
            title = { Text(title) },
            text = { Text("This will be available in a future update.") },
            confirmButton = { TextButton(onClick = { comingSoonTitle = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun HeaderCard(companyName: String, formattedDate: String) {
    RosterCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = companyName.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = BrandIndigoStrong,
                )
                Text(
                    text = "Manager Portal",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BrandIndigoStrong.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.WorkspacePremium, contentDescription = null, tint = BrandIndigoStrong)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricsGrid(state: DashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                value = "${state.activeStaffCount} / ${state.totalShiftsCount}",
                label = "Active Staff",
                icon = Icons.Filled.Groups,
                tint = AccentEmeraldLight,
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                value = "%.1fh".format(state.totalScheduledHours),
                label = "Hours Scheduled",
                icon = Icons.Filled.Schedule,
                tint = BrandIndigoStrong,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                value = "${state.completedTasksCount} / ${state.totalTasksCount}",
                label = "Tasks Completed",
                icon = Icons.Filled.Checklist,
                tint = AccentEmeraldLight,
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                value = "${state.pendingTimesheetsCount} Awaiting",
                label = "Pending Timesheets",
                icon = Icons.Filled.PendingActions,
                tint = if (state.pendingTimesheetsCount > 0) WarningAmberLight else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    RosterCard(modifier = modifier, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    onNewShift: () -> Unit,
    onNewTask: () -> Unit,
    onStaffDirectory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Quick Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionButton(
                modifier = Modifier.weight(1f),
                title = "New Shift",
                icon = Icons.Filled.EditCalendar,
                tint = BrandIndigoStrong,
                onClick = onNewShift,
            )
            QuickActionButton(
                modifier = Modifier.weight(1f),
                title = "New Task",
                icon = Icons.AutoMirrored.Filled.Assignment,
                tint = AccentEmeraldLight,
                onClick = onNewTask,
            )
            QuickActionButton(
                modifier = Modifier.weight(1f),
                title = "Staff Directory",
                icon = Icons.Filled.People,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onStaffDirectory,
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RosterCard(modifier = modifier, onClick = onClick, contentPadding = 14.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ActiveRosterSection(rows: List<RosterRowUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Today's Roster Status")
        if (rows.isEmpty()) {
            RosterCard {
                Text(
                    text = "No shifts scheduled for today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            RosterCard(contentPadding = 0.dp) {
                rows.forEachIndexed { index, row ->
                    RosterStatusRow(row)
                    if (index < rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun statusTint(status: ManagerShiftStatus): Color = when (status) {
    ManagerShiftStatus.Scheduled -> MaterialTheme.colorScheme.onSurfaceVariant
    ManagerShiftStatus.InProgress -> BrandIndigoStrong
    ManagerShiftStatus.PendingSubmission -> StatusColors.Pending
    ManagerShiftStatus.AwaitingApproval -> StatusColors.Scheduled
    ManagerShiftStatus.Approved -> StatusColors.Approved
    ManagerShiftStatus.Rejected -> StatusColors.Rejected
    ManagerShiftStatus.Absence -> StatusColors.Absent
}

@Composable
private fun RosterStatusRow(row: RosterRowUi) {
    val inProgress = row.status == ManagerShiftStatus.InProgress
    val tint = statusTint(row.status)

    Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(if (inProgress) BrandIndigoStrong.copy(alpha = 0.08f) else Color.Transparent)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (inProgress) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = "${row.role} • ${row.timeRange}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.jobsTotal > 0) {
                    val doneAll = row.jobsDone == row.jobsTotal
                    val jobsTint = if (doneAll) AccentEmeraldLight else WarningAmberLight
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = if (doneAll) Icons.Filled.CheckCircle else Icons.Filled.Checklist,
                            contentDescription = null,
                            tint = jobsTint,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "Jobs ${row.jobsDone}/${row.jobsTotal}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = jobsTint,
                        )
                    }
                }
            }
            SoftTag(text = row.status.title, tint = tint)
        }
        if (inProgress) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(BrandIndigoStrong),
            )
        }
    }
}

@Composable
private fun RecentTasksSection(rows: List<TaskLogRowUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Today's Task Logs")
        if (rows.isEmpty()) {
            RosterCard {
                Text(
                    text = "No task completions logged today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            RosterCard(contentPadding = 0.dp) {
                rows.forEachIndexed { index, row ->
                    TaskLogRow(row)
                    if (index < rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskLogRow(row: TaskLogRowUi) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (row.verified) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
            contentDescription = null,
            tint = if (row.verified) AccentEmeraldLight else WarningAmberLight,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.taskTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "Assigned to ${row.staffName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.timeLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.verified) {
                Text(
                    text = if (row.hasPhoto) "Photo Verified" else "Completed",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = AccentEmeraldLight,
                )
            }
        }
    }
}
