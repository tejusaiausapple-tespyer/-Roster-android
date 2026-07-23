package com.surainvestments.roster.ui.staff.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.ClockSession
import com.surainvestments.roster.domain.model.HoursMetrics
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.StaffShiftDisplayStatus
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.ui.components.HeroCard
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.SectionHeader
import com.surainvestments.roster.ui.components.SoftTag
import com.surainvestments.roster.ui.components.StatTile
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.staff.roster.ShiftRowUi
import com.surainvestments.roster.ui.staff.roster.SubmitHoursSheet
import com.surainvestments.roster.ui.staff.shared.rememberAddToCalendarAction
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors

/** Android analogue of iOS's staff `HomeView` — greeting, today's shift, and a short look-ahead. */
@Composable
fun StaffHomeScreen(
    viewModel: StaffHomeViewModel = hiltViewModel(),
    clockInViewModel: ClockInViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
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

    if (state.isLoading) {
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = BrandIndigoStrong)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(LocalNavBarPadding.current)
            .padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            GreetingHeader(name = state.greetingName, formattedDate = state.formattedDate)
            TodaySection(
                shifts = state.todaysShifts,
                clockSession = clockSession,
                onSubmitHours = { shift, timesheet -> submitTarget = shift to timesheet },
                onAddToCalendar = addToCalendar,
            )
            HoursSection(metrics = state.metrics)
            UpcomingSection(shifts = state.upcomingShifts)
        }
    }
}

private fun isClockable(row: ShiftRowUi, session: ClockSession?): Boolean {
    if (session?.shiftId == row.shift.id) return true
    if (session != null) return false // busy on another shift
    return row.timesheet == null
}

@Composable
private fun GreetingHeader(name: String, formattedDate: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Hi, $name",
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Today")
        if (shifts.isEmpty()) {
            RosterCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "No shift scheduled today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            HeroCard {
                shifts.forEachIndexed { index, shift ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = shift.timeRange,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                            val subtitle = listOfNotNull(
                                shift.department?.takeIf { it.isNotBlank() },
                                shift.location?.takeIf { it.isNotBlank() },
                            ).joinToString(" • ")
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SoftTag(text = shift.status.title, tint = Color.White)
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "Add to Calendar",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onAddToCalendar(shift.shift) },
                            )
                        }
                    }
                    if (index < shifts.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.25f),
                        )
                    }
                }
            }
            shifts.filter { isClockable(it, clockSession) }.forEach { shift ->
                ClockInCard(
                    shift = shift.shift,
                    onSubmitHours = { onSubmitHours(shift.shift, shift.timesheet) },
                )
            }
        }
    }
}

@Composable
private fun HoursSection(metrics: HoursMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Approved Hours")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = RosterFormat.decimalHours(metrics.week),
                    label = "This week",
                    unit = "h",
                    icon = Icons.Filled.CalendarMonth,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = RosterFormat.decimalHours(metrics.month),
                    label = "This month",
                    unit = "h",
                    icon = Icons.Filled.EditCalendar,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = RosterFormat.decimalHours(metrics.year),
                    label = "This year",
                    unit = "h",
                    icon = Icons.Filled.BarChart,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = RosterFormat.decimalHours(metrics.all),
                    label = "All time",
                    unit = "h",
                    icon = Icons.Filled.AllInclusive,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun UpcomingSection(shifts: List<UpcomingShiftUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Upcoming Shifts")
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
private fun UpcomingRow(upcoming: UpcomingShiftUi) {
    val tint = statusTint(upcoming.shift.status)
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.12f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Filled.Schedule, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = upcoming.dayLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = upcoming.shift.timeRange,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SoftTag(text = upcoming.shift.status.title, tint = tint)
    }
}
