package com.surainvestments.roster.ui.staff.roster

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.ClockSession
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.domain.model.TimesheetStatus
import com.surainvestments.roster.domain.model.friendlyMessage
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.HapticEvent
import com.surainvestments.roster.ui.components.Haptics
import com.surainvestments.roster.ui.components.HeroCard
import com.surainvestments.roster.ui.components.HhmmPickerDialog
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ScreenPadding
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private fun Instant.toHHmm(): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(RosterCalendar.zoneId).format(this)

private fun seedStart(shift: Shift, existing: Timesheet?, clock: ClockSession?): String =
    existing?.actualStart?.takeIf { it.contains(":") }
        ?: clock?.paidStart(shift.startDateTime)?.toHHmm()
        ?: shift.rosteredStart

private fun seedEnd(shift: Shift, existing: Timesheet?, clock: ClockSession?): String =
    existing?.actualEnd?.takeIf { it.contains(":") }
        ?: clock?.let { session ->
            if (session.useRosteredEnd == true) shift.rosteredEnd else session.clockOutAt?.toHHmm()
        }
        ?: shift.rosteredEnd

private fun submitTitle(existing: Timesheet?): String = when (existing?.status) {
    TimesheetStatus.Rejected -> "Resubmit hours"
    TimesheetStatus.Pending, TimesheetStatus.Draft -> "Update hours"
    else -> "Submit hours"
}

/**
 * Full-screen sheet for submitting (or resubmitting) worked hours for [shift] — prefilled from
 * [existing] (a previous submission), then the device's recorded [ClockSession] for this shift,
 * then the rostered times. Mirrors iOS `SubmitHoursSheet`.
 *
 * The uncompleted-tasks warning iOS shows here is intentionally not ported yet — it depends on
 * the Tasks tab's data (Phase E), which doesn't exist in the Staff app yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitHoursSheet(
    shift: Shift,
    existing: Timesheet?,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: SubmitHoursViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val clockSession = remember(shift.id) { viewModel.clockSessionFor(shift.id) }
    var start by rememberSaveable { mutableStateOf(seedStart(shift, existing, clockSession)) }
    var end by rememberSaveable { mutableStateOf(seedEnd(shift, existing, clockSession)) }
    var breakMinutes by rememberSaveable {
        mutableStateOf(existing?.actualBreakMinutes ?: clockSession?.timesheetBreakMinutes() ?: shift.breakMinutes)
    }
    var notes by rememberSaveable { mutableStateOf(existing?.staffNotes ?: "") }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val workedHours = BusinessRules.calcWorkedHours(start, end, breakMinutes)
    val scheduledDiff = workedHours - shift.scheduledHours

    BackHandler(onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = ScreenPillTopBarHeight + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HeroCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = RosterFormat.dayHeader(shift.date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = RosterFormat.decimalHours(workedHours),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                        Text(
                            text = "hours worked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        )
                    }
                    if (kotlin.math.abs(scheduledDiff) > 0.5) {
                        Text(
                            text = if (scheduledDiff > 0) {
                                "${RosterFormat.decimalHours(scheduledDiff)}h more than scheduled"
                            } else {
                                "${RosterFormat.decimalHours(-scheduledDiff)}h less than scheduled"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            RosterCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TimeRow(title = "Start", value = start, onClick = { showStartPicker = true })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TimeRow(title = "End", value = end, onClick = { showEndPicker = true })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    BreakRow(
                        minutes = breakMinutes,
                        onDecrement = { breakMinutes = BusinessRules.clampBreakMinutes(breakMinutes - BusinessRules.breakMinutesStep) },
                        onIncrement = { breakMinutes = BusinessRules.clampBreakMinutes(breakMinutes + BusinessRules.breakMinutesStep) },
                    )
                }
            }

            RosterCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "NOTES (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Anything your manager should know?") },
                        minLines = 2,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }

            errorMessage?.let { Banner(kind = BannerKind.Error, title = it) }

            PrimaryButton(
                text = submitTitle(existing),
                loading = isWorking,
                enabled = !isWorking && workedHours > 0,
                onClick = {
                    errorMessage = null
                    if (workedHours <= 0) {
                        errorMessage = "Worked hours must be greater than zero."
                        return@PrimaryButton
                    }
                    scope.launch {
                        isWorking = true
                        try {
                            viewModel.submit(shift, existing, start, end, breakMinutes, workedHours, notes)
                            Haptics.perform(haptics, HapticEvent.SubmitSuccess)
                            onSubmitted()
                        } catch (e: Exception) {
                            Haptics.perform(haptics, HapticEvent.SubmitError)
                            errorMessage = friendlyMessage(e, "Something went wrong.")
                        } finally {
                            isWorking = false
                        }
                    }
                },
            )
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(title = "Submit Hours", onBack = onDismiss, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (showStartPicker) {
        HhmmPickerDialog(initial = start, title = "Start", onDismiss = { showStartPicker = false }, onConfirm = { start = it; showStartPicker = false })
    }
    if (showEndPicker) {
        HhmmPickerDialog(initial = end, title = "End", onDismiss = { showEndPicker = false }, onConfirm = { end = it; showEndPicker = false })
    }
}

@Composable
private fun TimeRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
        TextButton(onClick = onClick) {
            Text(text = RosterFormat.timeOfDay(value), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun BreakRow(minutes: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Break", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepButton(icon = Icons.Filled.Remove, contentDescription = "Decrease break", onClick = onDecrement)
            Text(
                text = "${minutes}m",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.size(width = 40.dp, height = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            StepButton(icon = Icons.Filled.Add, contentDescription = "Increase break", onClick = onIncrement)
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp).background(BrandIndigoStrong.copy(alpha = 0.12f), CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = BrandIndigoStrong, modifier = Modifier.size(16.dp))
    }
}

