package com.surainvestments.roster.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.PasswordField
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.RosterTextField
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import androidx.compose.ui.unit.dp

/**
 * Account → email pencil icon's sheet — the Android analogue of iOS's `ChangeEmailView`. Unlike
 * [ChangePasswordScreen], success here doesn't mean the email has actually changed yet (see
 * [AuthRepository.changeEmail]'s doc comment) — the form just stays in a confirmation state
 * until the user taps Close, rather than auto-dismissing.
 */
@Composable
fun ChangeEmailScreen(viewModel: ChangeEmailViewModel = hiltViewModel(), modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()

    // See ChangeEmailViewModel.resetForNextOpen's doc comment — this dialog has no back-stack
    // entry of its own, so without this a reopen would show the previous request's stale
    // "check your inbox" confirmation instead of a fresh form.
    DisposableEffect(Unit) {
        onDispose { viewModel.resetForNextOpen() }
    }

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
            Text(text = "Change email", style = MaterialTheme.typography.headlineSmall)

            if (uiState.succeeded) {
                Banner(
                    kind = BannerKind.Success,
                    title = "Check your inbox",
                    message = "We sent a confirmation link to ${uiState.newEmail.trim()}. Your current email stays active until you confirm it.",
                )
            } else {
                Text(
                    text = "For your security, confirm your password and the new address. We'll email a confirmation link before anything changes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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
                RosterTextField(
                    value = uiState.newEmail,
                    onValueChange = viewModel::onNewEmailChange,
                    label = "New email",
                    leadingIcon = Icons.Outlined.Email,
                    enabled = !uiState.isWorking,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                PrimaryButton(
                    text = "Send confirmation link",
                    onClick = viewModel::submit,
                    enabled = uiState.canSubmit,
                    loading = uiState.isWorking,
                )
            }
        }
    }
}
