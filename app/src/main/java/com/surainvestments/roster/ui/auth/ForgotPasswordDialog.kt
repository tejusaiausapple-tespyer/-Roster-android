package com.surainvestments.roster.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** Android analogue of iOS's `ForgotPasswordSheet`, presented from the Login screen. */
@Composable
fun ForgotPasswordDialog(
    prefilledEmail: String,
    onDismiss: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.prefill(prefilledEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (uiState.sent) "Check your email" else "Reset your password") },
        text = {
            if (uiState.sent) {
                Text("If an account exists for ${uiState.email}, a password reset link has been sent.")
            } else {
                Column {
                    Text(
                        text = "Enter your account email and we'll send you a link to reset your password.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    uiState.errorMessage?.let { message ->
                        Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !uiState.isWorking,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (uiState.sent) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(onClick = viewModel::send, enabled = !uiState.isWorking) { Text("Send reset link") }
            }
        },
        dismissButton = {
            if (!uiState.sent) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
