package com.surainvestments.roster.ui.manager.staff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.surainvestments.roster.ui.components.PasswordField

@Composable
fun ChangeEmailDialog(
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (newEmail: String, managerPassword: String) -> Unit,
) {
    var newEmail by remember { mutableStateOf("") }
    var managerPassword by remember { mutableStateOf("") }
    val canSubmit = newEmail.isNotBlank() && managerPassword.isNotBlank() && !isWorking

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change email") },
        text = {
            Column {
                Text(
                    "Confirm your own password to change this staff member's sign-in email.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    label = { Text("New email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(
                    value = managerPassword,
                    onValueChange = { managerPassword = it },
                    label = "Your password",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canSubmit, onClick = { onSubmit(newEmail, managerPassword) }) {
                Text(if (isWorking) "Working…" else "Change")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
