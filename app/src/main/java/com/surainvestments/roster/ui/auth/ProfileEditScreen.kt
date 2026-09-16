package com.surainvestments.roster.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.QuietOutlinedButton
import com.surainvestments.roster.ui.components.RosterTextField
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.ContentMaxWidth

/**
 * The [com.surainvestments.roster.domain.routing.AppRoute.ProfileCompletion] gate — shown before
 * a staff member with a missing dob/address/phone (or a manager-flagged
 * `profileUpdateRequired`) can reach the app at all. Requires those three fields before it can be
 * submitted, matching [com.surainvestments.roster.domain.model.AppUser.needsProfileCompletion]'s
 * own completeness check, so a successful save always clears the gate (the route recomputes
 * reactively off the same live profile listener). Includes a Sign Out escape hatch, mirroring
 * [ChangePasswordScreen]'s forced-mode convention — there is otherwise no way out of this screen.
 */
@Composable
fun ProfileCompletionScreen(viewModel: ProfileEditViewModel = hiltViewModel(), modifier: Modifier = Modifier) {
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
            Text(text = "Complete your profile", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Your manager needs a few more details before you can continue.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ProfileEditFields(uiState = uiState, viewModel = viewModel)

            PrimaryButton(
                text = "Save and continue",
                onClick = viewModel::submit,
                enabled = uiState.canSubmit(requireCompletion = true),
                loading = uiState.isWorking,
            )
            QuietOutlinedButton(text = "Sign out", onClick = viewModel::signOut)
        }
    }
}

/**
 * Account tab's optional "Edit profile" screen — same fields/write path as
 * [ProfileCompletionScreen], but nothing is required (a staff member can save a partial edit or
 * clear a field). Pushed the same way as [com.surainvestments.roster.ui.screens.VersionHistoryScreen].
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)

    LaunchedEffect(uiState.succeeded) {
        if (uiState.succeeded) onSaved()
    }

    // See ProfileEditViewModel.resetForNextOpen's doc comment — this screen has no back-stack
    // entry of its own, so without this the same ViewModel instance (and its stale `succeeded`/
    // unsaved-draft state) would resurface, silently bouncing straight back out, the next time
    // this sheet is reopened.
    DisposableEffect(Unit) {
        onDispose { viewModel.resetForNextOpen() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(LocalNavBarPadding.current)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(ScreenPillTopBarHeight))

                ProfileEditFields(uiState = uiState, viewModel = viewModel)

                PrimaryButton(
                    text = "Save changes",
                    onClick = viewModel::submit,
                    enabled = uiState.canSubmit(requireCompletion = false),
                    loading = uiState.isWorking,
                )
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "Edit Profile",
            icon = Icons.Outlined.Edit,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun ProfileEditFields(uiState: ProfileEditUiState, viewModel: ProfileEditViewModel) {
    uiState.errorMessage?.let { message ->
        Banner(kind = BannerKind.Error, title = message)
    }

    RosterTextField(
        value = uiState.fullName,
        onValueChange = viewModel::onFullNameChange,
        label = "Full name",
        leadingIcon = Icons.Outlined.Person,
        enabled = !uiState.isWorking,
        modifier = Modifier.fillMaxWidth(),
    )
    RosterTextField(
        value = uiState.phone,
        onValueChange = viewModel::onPhoneChange,
        label = "Phone",
        leadingIcon = Icons.Outlined.Phone,
        enabled = !uiState.isWorking,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
    RosterTextField(
        value = uiState.dob,
        onValueChange = viewModel::onDobChange,
        label = "Date of birth (YYYY-MM-DD)",
        leadingIcon = Icons.Outlined.Cake,
        enabled = !uiState.isWorking,
        modifier = Modifier.fillMaxWidth(),
    )
    RosterTextField(
        value = uiState.address,
        onValueChange = viewModel::onAddressChange,
        label = "Address",
        leadingIcon = Icons.Outlined.Home,
        enabled = !uiState.isWorking,
        modifier = Modifier.fillMaxWidth(),
    )
    RosterTextField(
        value = uiState.emergencyContact,
        onValueChange = viewModel::onEmergencyContactChange,
        label = "Emergency contact (name & phone)",
        leadingIcon = Icons.Outlined.ContactPhone,
        enabled = !uiState.isWorking,
        modifier = Modifier.fillMaxWidth(),
    )
}
