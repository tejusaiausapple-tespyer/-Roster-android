package com.surainvestments.roster.ui.staff.availability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.surainvestments.roster.domain.model.DayAvailability
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Weekday
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.HhmmPickerDialog
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.RosterSwitch
import com.surainvestments.roster.ui.components.SecondaryButton

/** Available → All-day → From/Until day editor. Mirrors iOS `DayEditSheet`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditSheet(
    weekday: Weekday,
    dateLabel: String,
    initial: DayAvailability,
    onDismiss: () -> Unit,
    onSave: (DayAvailability) -> Unit,
) {
    var available by remember(initial) { mutableStateOf(initial.available) }
    var allDay by remember(initial) { mutableStateOf(initial.allDay) }
    var start by remember(initial) { mutableStateOf(initial.start ?: "09:00") }
    var end by remember(initial) { mutableStateOf(initial.end ?: "17:00") }
    var error by remember { mutableStateOf<String?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = weekday.fullLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            RosterCard {
                ToggleRow(label = "Available", checked = available, onCheckedChange = { available = it })
            }

            if (available) {
                RosterCard {
                    ToggleRow(label = "Available all day", checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    RosterCard {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            TimeRow(title = "From", value = start, onClick = { showStartPicker = true })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            TimeRow(title = "Until", value = end, onClick = { showEndPicker = true })
                        }
                    }
                }
            } else {
                Text(
                    text = "You'll be marked as unavailable for this day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            error?.let { Banner(kind = BannerKind.Error, title = it) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = "Done",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (available && !allDay && start >= end) {
                            error = "End time must be after start time."
                            return@PrimaryButton
                        }
                        onSave(
                            DayAvailability(
                                available = available,
                                allDay = allDay,
                                start = if (available && !allDay) start else null,
                                end = if (available && !allDay) end else null,
                            ),
                        )
                    },
                )
            }
        }
    }

    if (showStartPicker) {
        HhmmPickerDialog(initial = start, title = "From", onDismiss = { showStartPicker = false }, onConfirm = { start = it; showStartPicker = false })
    }
    if (showEndPicker) {
        HhmmPickerDialog(initial = end, title = "Until", onDismiss = { showEndPicker = false }, onConfirm = { end = it; showEndPicker = false })
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
        RosterSwitch(checked = checked, onCheckedChange = onCheckedChange)
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
