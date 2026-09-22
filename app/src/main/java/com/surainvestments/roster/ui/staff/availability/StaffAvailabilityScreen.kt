package com.surainvestments.roster.ui.staff.availability

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.DayAvailability
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Weekday
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.HapticEvent
import com.surainvestments.roster.ui.components.Haptics
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.RosterSwitch
import com.surainvestments.roster.ui.components.ScreenLoadingSkeleton
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.SoftTag
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors

/** Android analogue of iOS's staff `AvailabilityView` — a −2…+12 week editor. */
@Composable
fun StaffAvailabilityScreen(
    viewModel: StaffAvailabilityViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val navBarPadding = LocalNavBarPadding.current
    val haptics = LocalHapticFeedback.current
    var editingDay by remember { mutableStateOf<Weekday?>(null) }
    var showRecurringConfirm by remember { mutableStateOf(false) }

    var wasSaving by remember { mutableStateOf(false) }
    LaunchedEffect(isSaving, errorMessage) {
        if (wasSaving && !isSaving) {
            Haptics.perform(haptics, if (errorMessage != null) HapticEvent.SaveError else HapticEvent.SaveSuccess)
        }
        wasSaving = isSaving
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.isLoading) {
            ScreenLoadingSkeleton(modifier = Modifier.fillMaxSize(), itemCount = 7, itemHeight = 52.dp)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(navBarPadding)
                    .padding(top = ScreenPillTopBarHeight + 4.dp)
                    .padding(horizontal = ScreenPadding, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    WeekNav(state = state, onPrev = viewModel::onPrevWeek, onNext = viewModel::onNextWeek, onToday = viewModel::onToday)

                    when {
                        state.isEmployerLocked -> Banner(
                            kind = BannerKind.Info,
                            title = "Locked by your manager",
                            message = "The roster for this week has been published and locked. Contact your manager to change availability.",
                        )
                        state.isLocked -> Banner(
                            kind = BannerKind.Info,
                            title = "This week is locked",
                            message = "You can only change availability for upcoming weeks. Navigate forward to edit.",
                        )
                    }

                    RosterCard(contentPadding = 0.dp) {
                        Weekday.entries.forEachIndexed { index, weekday ->
                            DayRow(
                                weekday = weekday,
                                value = state.days[weekday] ?: DayAvailability.defaultDay,
                                locked = state.isLocked,
                                onClick = { if (!state.isLocked) editingDay = weekday },
                            )
                            if (index < Weekday.entries.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }

                    if (!state.isLocked) {
                        RosterCard {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(text = "Set as recurring", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    Text(
                                        text = "Apply to this and all upcoming weeks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                RosterSwitch(checked = state.saveAsDefault, onCheckedChange = viewModel::onToggleSaveAsDefault)
                            }
                        }

                        errorMessage?.let { Banner(kind = BannerKind.Error, title = it) }

                        PrimaryButton(
                            text = "Save",
                            loading = isSaving,
                            enabled = state.isDirty && !isSaving,
                            onClick = {
                                if (state.saveAsDefault) showRecurringConfirm = true else viewModel.save()
                            },
                        )
                    }
                }
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(title = "Availability", icon = Icons.Filled.CalendarMonth, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (showRecurringConfirm) {
        AlertDialog(
            onDismissRequest = { showRecurringConfirm = false },
            title = { Text("Set as default?") },
            text = { Text("This overwrites your availability for this week and every upcoming unlocked week.") },
            confirmButton = {
                TextButton(onClick = { showRecurringConfirm = false; viewModel.save() }) { Text("Set as default") }
            },
            dismissButton = { TextButton(onClick = { showRecurringConfirm = false }) { Text("Cancel") } },
        )
    }

    editingDay?.let { weekday ->
        val dateKey = RosterCalendar.weekDayKeys(state.weekKey).getOrNull(Weekday.entries.indexOf(weekday)) ?: state.weekKey
        DayEditSheet(
            weekday = weekday,
            dateLabel = RosterFormat.dateShort(dateKey),
            initial = state.days[weekday] ?: DayAvailability.defaultDay,
            onDismiss = { editingDay = null },
            onSave = { value -> viewModel.onDayUpdated(weekday, value); editingDay = null },
        )
    }
}

@Composable
private fun WeekNav(state: AvailabilityUiState, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    RosterCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            NavIconButton(icon = Icons.Filled.ChevronLeft, enabled = state.canGoPrev, contentDescription = "Previous week", onClick = onPrev)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.isLocked) {
                        Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                    Text(text = state.weekRangeLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = state.relativeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = BrandIndigoStrong,
                        modifier = Modifier.clickable(enabled = state.relativeLabel != "This week", onClick = onToday),
                    )
                    SoftTag(
                        text = if (state.isCustomWeek) "Custom" else "Default",
                        tint = if (state.isCustomWeek) BrandIndigoStrong else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            NavIconButton(icon = Icons.Filled.ChevronRight, enabled = state.canGoNext, contentDescription = "Next week", onClick = onNext)
        }
    }
}

@Composable
private fun NavIconButton(icon: ImageVector, enabled: Boolean, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).background(BrandIndigoStrong.copy(alpha = if (enabled) 0.10f else 0.04f), CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = if (enabled) BrandIndigoStrong else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayRow(weekday: Weekday, value: DayAvailability, locked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !locked, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (value.available) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            tint = if (value.available) StatusColors.Approved else StatusColors.Rejected,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = weekday.fullLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(text = daySummary(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!locked) {
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun daySummary(day: DayAvailability): String = when {
    !day.available -> "Unavailable"
    day.allDay -> "Available all day"
    else -> "${RosterFormat.timeOfDay(day.start ?: "09:00")} – ${RosterFormat.timeOfDay(day.end ?: "17:00")}"
}
