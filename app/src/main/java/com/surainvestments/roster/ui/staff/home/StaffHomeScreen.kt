package com.surainvestments.roster.ui.staff.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.ClockSession
import com.surainvestments.roster.domain.model.HoursMetrics
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.StaffShiftDisplayStatus
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.ui.components.LinkButton
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenLoadingSkeleton
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.SoftTag
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.staff.roster.ShiftRowUi
import com.surainvestments.roster.ui.staff.roster.SubmitHoursSheet
import com.surainvestments.roster.ui.staff.shared.rememberAddToCalendarAction
import com.surainvestments.roster.ui.theme.AccentEmeraldLight
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors
import com.surainvestments.roster.ui.theme.WarningAmberLight
import java.time.Duration
import java.time.LocalTime
import java.util.Locale

/** Android analogue of iOS's staff `HomeView` — header pill, greeting, today's shift, daily jobs, tasks, hours snapshot, timesheets, up next. */
@Composable
fun StaffHomeScreen(
    onOpenTasks: () -> Unit = {},
    onOpenDailyJobs: () -> Unit = {},
    onOpenRoster: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StaffHomeViewModel = hiltViewModel(),
    clockInViewModel: ClockInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val clockSession by clockInViewModel.session.collectAsState()
    var submitTarget by remember { mutableStateOf<Pair<Shift, Timesheet?>?>(null) }
    val addToCalendar = rememberAddToCalendarAction()

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
        if (state.isLoading) {
            ScreenLoadingSkeleton(modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(LocalNavBarPadding.current)
                    .padding(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        top = ScreenPillTopBarHeight + 4.dp,
                        bottom = 24.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    CompanyPillHeader(companyName = state.companyName)
                    GreetingHeader(greetingName = state.greetingName, formattedDate = state.formattedDate)

                    TodaySection(
                        shifts = state.todaysShifts,
                        clockSession = clockSession,
                        onSubmitHours = { shift, timesheet -> submitTarget = shift to timesheet },
                        onAddToCalendar = addToCalendar,
                    )

                    DailyJobsCard(snapshot = state.dailyJobsSnapshot, onClick = onOpenDailyJobs)
                    TasksCard(snapshot = state.tasksSnapshot, onClick = onOpenTasks)

                    HoursSnapshotSection(metrics = state.metrics)
                    TimesheetsSection(
                        snapshot = state.timesheetsSnapshot,
                        onSubmitHours = { shift, timesheet -> submitTarget = shift to timesheet },
                    )
                    UpNextSection(shifts = state.upcomingShifts, onViewRoster = onOpenRoster)
                }
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "Home",
            icon = Icons.Filled.Home,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun CompanyPillHeader(companyName: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = companyName.uppercase(Locale.ENGLISH),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = BrandIndigoStrong,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(BrandIndigoStrong.copy(alpha = 0.08f))
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun GreetingHeader(greetingName: String, formattedDate: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = greetingName,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodaySection(
    shifts: List<ShiftRowUi>,
    clockSession: ClockSession?,
    onSubmitHours: (Shift, Timesheet?) -> Unit,
    onAddToCalendar: (Shift) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "☀️", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        if (shifts.isEmpty()) {
            RosterCard {
                Text(
                    text = "No shift scheduled today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            shifts.forEach { shiftUi ->
                HeroShiftCard(
                    shiftUi = shiftUi,
                    clockSession = clockSession,
                    onSubmitHours = onSubmitHours,
                    onAddToCalendar = onAddToCalendar,
                )
            }
        }
    }
}

@Composable
private fun HeroShiftCard(
    shiftUi: ShiftRowUi,
    clockSession: ClockSession?,
    onSubmitHours: (Shift, Timesheet?) -> Unit,
    onAddToCalendar: (Shift) -> Unit,
) {
    val shift = shiftUi.shift
    val durationText = formatDuration(shift.rosteredStart, shift.rosteredEnd)

    RosterCard(contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WorkOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Today's shift",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SoftTag(text = "• ${shiftUi.status.title}", tint = statusTint(shiftUi.status))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .clickable { onAddToCalendar(shift) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EditCalendar,
                            contentDescription = "Add to Calendar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = shiftUi.timeRange,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                if (durationText.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isClockable(shiftUi, clockSession)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ClockInCard(
                    shift = shift,
                    onSubmitHours = { onSubmitHours(shift, shiftUi.timesheet) },
                )
            }
        }
    }
}

@Composable
private fun DailyJobsCard(snapshot: DailyJobsSnapshotUi, onClick: () -> Unit) {
    RosterCard(onClick = onClick, contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (snapshot.totalCount > 0 && snapshot.pendingCount == 0)
                            AccentEmeraldLight.copy(alpha = 0.15f)
                        else WarningAmberLight.copy(alpha = 0.15f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = snapshot.doneCount.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (snapshot.totalCount > 0 && snapshot.pendingCount == 0)
                        AccentEmeraldLight else WarningAmberLight,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Daily Jobs",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                val subtitle = when {
                    snapshot.totalCount == 0 -> "No daily jobs assigned today"
                    snapshot.pendingCount == 0 -> "All ${snapshot.totalCount} completed"
                    else -> "${snapshot.pendingCount} remaining · ${snapshot.doneCount} of ${snapshot.totalCount} done"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TasksCard(snapshot: TasksSnapshotUi, onClick: () -> Unit) {
    RosterCard(onClick = onClick, contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentEmeraldLight.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = AccentEmeraldLight,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Tasks",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                val subtitle = when {
                    snapshot.totalCount == 0 -> "No tasks assigned today"
                    snapshot.pendingCount == 0 -> "All ${snapshot.totalCount} completed"
                    else -> "${snapshot.pendingCount} remaining · ${snapshot.doneCount} of ${snapshot.totalCount} done"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HoursSnapshotSection(metrics: HoursMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "📊", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Hours snapshot",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        RosterCard(contentPadding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = RosterFormat.decimalHours(metrics.week),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "hours this week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    HourStatCol(
                        value = "${RosterFormat.decimalHours(metrics.month)}h",
                        label = "Month",
                        modifier = Modifier.weight(1f),
                    )
                    HourStatCol(
                        value = "${RosterFormat.decimalHours(metrics.year)}h",
                        label = "Year",
                        modifier = Modifier.weight(1f),
                    )
                    HourStatCol(
                        value = "${RosterFormat.decimalHours(metrics.all)}h",
                        label = "All time",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HourStatCol(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimesheetsSection(
    snapshot: TimesheetsSnapshotUi,
    onSubmitHours: (Shift, Timesheet?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "⏱", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Timesheets",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        if (snapshot.pendingItems.isEmpty()) {
            RosterCard(contentPadding = 14.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AccentEmeraldLight.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = AccentEmeraldLight,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Everything is up to date",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "You have no missed timesheets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            snapshot.pendingItems.forEach { item ->
                RosterCard(contentPadding = 14.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (item.isRejected) MaterialTheme.colorScheme.errorContainer
                                    else WarningAmberLight.copy(alpha = 0.15f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PriorityHigh,
                                contentDescription = null,
                                tint = if (item.isRejected) MaterialTheme.colorScheme.error else WarningAmberLight,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Timesheet required for ${item.dateLabel}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            val details = listOfNotNull(
                                "Rostered: ${item.timeRangeLabel}",
                                item.location?.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (item.isRejected) {
                                Text(
                                    text = "Rejected by manager — tap to resubmit",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Button(
                            onClick = { onSubmitHours(item.shift, item.timesheet) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandIndigoStrong,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(
                                text = if (item.isRejected) "Resubmit" else "Submit",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpNextSection(shifts: List<UpcomingShiftUi>, onViewRoster: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "📅", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Up next",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            LinkButton(text = "View roster", onClick = onViewRoster)
        }
        if (shifts.isEmpty()) {
            RosterCard {
                Text(
                    text = "Nothing else scheduled yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            RosterCard(contentPadding = 0.dp) {
                shifts.forEachIndexed { index, upcoming ->
                    UpcomingRow(upcoming)
                    if (index < shifts.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(upcoming: UpcomingShiftUi) {
    val dateKey = upcoming.dateKey
    val dayShort = RosterFormat.weekdayShort(dateKey).uppercase(Locale.ENGLISH)
    val dayNum = RosterCalendar.parseDateKey(dateKey)?.dayOfMonth?.toString() ?: ""
    val shift = upcoming.shift.shift
    val durationText = formatDuration(shift.rosteredStart, shift.rosteredEnd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.small)
                .background(BrandIndigoStrong.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dayShort,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    ),
                    color = BrandIndigoStrong,
                )
                Text(
                    text = dayNum,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = BrandIndigoStrong,
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val dateLine = "${RosterFormat.dayHeader(dateKey)}, ${upcoming.shift.timeRange}"
            Text(
                text = dateLine,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            val subParts = listOfNotNull(
                shift.location?.takeIf { it.isNotBlank() },
                durationText.takeIf { it.isNotBlank() },
            )
            if (subParts.isNotEmpty()) {
                Text(
                    text = subParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(startHhmm: String, endHhmm: String): String {
    val start = runCatching { LocalTime.parse(startHhmm) }.getOrNull() ?: return ""
    val end = runCatching { LocalTime.parse(endHhmm) }.getOrNull() ?: return ""
    var mins = Duration.between(start, end).toMinutes()
    if (mins < 0) mins += 24 * 60
    val h = mins / 60
    val m = mins % 60
    return if (m > 0) "${h}h ${m}m" else "${h}h"
}

private fun isClockable(row: ShiftRowUi, session: ClockSession?): Boolean {
    if (session?.shiftId == row.shift.id) return true
    if (session != null) return false // busy on another shift
    return row.timesheet == null
}

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
