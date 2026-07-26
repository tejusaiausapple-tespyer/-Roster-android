package com.surainvestments.roster.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val hhmmFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Time picker dialog over an "HH:mm" string — shared by Submit Hours and Availability's day
 * editor. Uses the standard Material 3 [TimePicker] dial for a consistent Android 14+ feel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HhmmPickerDialog(
    initial: String,
    title: String = "Select time",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val seedTime = runCatching { LocalTime.parse(initial, hhmmFormatter) }.getOrDefault(LocalTime.NOON)
    val state = rememberTimePickerState(
        initialHour = seedTime.hour,
        initialMinute = seedTime.minute,
        is24Hour = false // User sees AM/PM dial
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(state.hour, state.minute).format(hhmmFormatter))
            }) { Text("OK", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Box(
                modifier = Modifier.padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = state)
            }
        },
    )
}
