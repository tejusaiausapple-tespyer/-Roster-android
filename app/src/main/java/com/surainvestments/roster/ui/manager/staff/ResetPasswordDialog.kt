package com.surainvestments.roster.ui.manager.staff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.surainvestments.roster.domain.model.PasswordRules
import com.surainvestments.roster.ui.components.PasswordField

@Composable
fun ResetPasswordDialog(
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (temporaryPassword: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val canSubmit = PasswordRules.errors(password).isEmpty() && !isWorking

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset password") },
        text = {
            Column {
                Text(
                    "Sets a temporary password. The staff member must change it on next login.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Temporary password",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canSubmit, onClick = { onSubmit(password) }) {
                Text(if (isWorking) "Working…" else "Reset")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
