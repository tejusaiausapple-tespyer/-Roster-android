package com.surainvestments.roster.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.PasswordField
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.QuietOutlinedButton
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.theme.ContentMaxWidth

@Composable
fun ChangePasswordScreen(
    isForced: Boolean,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isForced) {
                Text(text = "Set a new password", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "For your security, please choose a new password before continuing.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(text = "Change password", style = MaterialTheme.typography.headlineSmall)
            }

            uiState.errorMessage?.let { message ->
                Banner(kind = BannerKind.Error, title = message)
            }

            PasswordField(
                value = uiState.currentPassword,
                onValueChange = viewModel::onCurrentPasswordChange,
                label = "Current password",
                enabled = !uiState.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = uiState.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = "New password",
                enabled = !uiState.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = "Confirm new password",
                enabled = !uiState.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )

            RosterCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Password requirements",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    uiState.rules.forEach { rule ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = if (rule.isMet) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (rule.isMet) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = rule.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (rule.isMet) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            PrimaryButton(
                text = "Update password",
                onClick = { viewModel.submit(isForced) },
                enabled = uiState.canSubmit,
                loading = uiState.isWorking,
            )

            if (isForced) {
                QuietOutlinedButton(text = "Sign out", onClick = viewModel::signOut)
            }
        }
    }
}
