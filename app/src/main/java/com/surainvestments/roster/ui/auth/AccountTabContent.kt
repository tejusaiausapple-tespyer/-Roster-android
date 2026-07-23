package com.surainvestments.roster.ui.auth

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.surainvestments.roster.BuildConfig
import com.surainvestments.roster.data.local.AppearanceMode
import com.surainvestments.roster.domain.model.AccountDeletionStatus
import com.surainvestments.roster.domain.model.AccountDeletionState
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.UserRole
import com.surainvestments.roster.domain.model.UserStatus
import com.surainvestments.roster.ui.components.MiniStatCard
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.components.SettingsDivider
import com.surainvestments.roster.ui.components.SettingsRow
import com.surainvestments.roster.ui.components.SettingsSection
import com.surainvestments.roster.ui.components.SettingsToggleRow
import com.surainvestments.roster.ui.components.SoftTag
import com.surainvestments.roster.ui.manager.staff.StaffListScreen
import com.surainvestments.roster.ui.screens.PrivacyPolicyScreen
import com.surainvestments.roster.ui.screens.TermsOfServiceScreen
import com.surainvestments.roster.ui.staff.payslips.PayslipsScreen
import com.surainvestments.roster.ui.theme.AccentEmeraldLight
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import com.surainvestments.roster.ui.theme.ErrorRedLight
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.TextSecondaryLight
import com.surainvestments.roster.ui.theme.TextTertiaryLight
import com.surainvestments.roster.ui.theme.WarningAmberLight
import kotlinx.coroutines.launch

private const val SUPPORT_EMAIL = "support@sura-roster.com"

/**
 * Account tab — layout mirrors iOS `AccountView` / `ManagerAccountView`
 * (inset-grouped sections, profile hero, security, about, sign out).
 */
@Composable
fun AccountTabContent(
    authViewModel: AuthViewModel,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    deletionViewModel: AccountDeletionViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as FragmentActivity
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceAuthEnabled by authViewModel.isDeviceAuthEnabled.collectAsState()
    val ownProfile by authViewModel.ownProfile.collectAsState()
    val deletionState by deletionViewModel.uiState.collectAsState()
    val isManager = ownProfile?.role == UserRole.Manager

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showStaffDirectory by remember { mutableStateOf(false) }
    var showTermsOfService by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showPayslips by remember { mutableStateOf(false) }
    var emailVerified by remember { mutableStateOf(false) }
    var comingSoonTitle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        emailVerified = FirebaseAuth.getInstance().currentUser?.isEmailVerified == true
    }

    if (showStaffDirectory && isManager) {
        StaffListScreen(
            onBack = { showStaffDirectory = false },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    if (showTermsOfService) {
        TermsOfServiceScreen(
            onBack = { showTermsOfService = false },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    if (showPrivacyPolicy) {
        PrivacyPolicyScreen(
            onBack = { showPrivacyPolicy = false },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    if (showPayslips) {
        PayslipsScreen(
            onBack = { showPayslips = false },
            modifier = modifier.fillMaxSize(),
        )
        return
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
            .padding(LocalNavBarPadding.current)
            .padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(modifier = Modifier.height(ScreenPillTopBarHeight))

            ProfileHero(user = ownProfile)

            if (ownProfile?.emailChangeRequired == true) {
                SettingsSection {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Update your email",
                            style = MaterialTheme.typography.titleSmall,
                            color = WarningAmberLight,
                        )
                        Text(
                            text = "Your manager asked you to update your email address.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            DetailsSection(user = ownProfile, emailVerified = emailVerified)

            if (!isManager) {
                StaffStatsSection()
                SettingsSection(title = "Pay") {
                    SettingsRow(
                        title = "Payslips",
                        icon = Icons.Outlined.Payments,
                        showChevron = true,
                        onClick = { showPayslips = true },
                    )
                }
            }

            if (isManager) {
                SettingsSection(title = "Business") {
                    SettingsRow(
                        title = "Company details",
                        icon = Icons.Outlined.Business,
                        showChevron = true,
                        onClick = { comingSoonTitle = "Company details" },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Locations",
                        icon = Icons.Outlined.LocationOn,
                        showChevron = true,
                        onClick = { comingSoonTitle = "Locations" },
                    )
                }
                SettingsSection(title = "Management") {
                    SettingsRow(
                        title = "Staff",
                        icon = Icons.Outlined.People,
                        showChevron = true,
                        onClick = { showStaffDirectory = true },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Availability",
                        icon = Icons.Outlined.EditCalendar,
                        showChevron = true,
                        onClick = { comingSoonTitle = "Availability" },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Reports",
                        icon = Icons.Outlined.BarChart,
                        showChevron = true,
                        onClick = { comingSoonTitle = "Reports" },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Tenure & Hours",
                        icon = Icons.Outlined.WorkspacePremium,
                        showChevron = true,
                        onClick = { comingSoonTitle = "Tenure & Hours" },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Wage",
                        icon = Icons.Outlined.Payments,
                        showChevron = true,
                        onClick = { comingSoonTitle = "Wage" },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Payroll",
                        icon = Icons.AutoMirrored.Outlined.Assignment,
                        showChevron = true,
                        onClick = { comingSoonTitle = "Payroll" },
                    )
                }
            }

            SettingsSection(
                title = "Notifications",
                footer = if (!isManager) {
                    "Shift start and hours reminders are scheduled on this device from your last roster sync."
                } else {
                    null
                },
            ) {
                SettingsRow(
                    title = if (isManager) "Push notifications" else "Alerts allowed",
                    icon = Icons.Outlined.Notifications,
                    value = "Off",
                    valueColor = TextTertiaryLight,
                )
                SettingsDivider()
                SettingsRow(
                    title = if (isManager) "Notification settings" else "System notification settings",
                    icon = Icons.Outlined.Settings,
                    showChevron = true,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            },
                        )
                    },
                )
            }

            SettingsSection(title = "Appearance") {
                SettingsToggleRow(
                    title = "Dark Mode",
                    icon = Icons.Outlined.DarkMode,
                    checked = when (appearanceMode) {
                        AppearanceMode.Dark -> true
                        AppearanceMode.Light -> false
                        AppearanceMode.System ->
                            (LocalConfiguration.current.uiMode and
                                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                                android.content.res.Configuration.UI_MODE_NIGHT_YES
                    },
                    onCheckedChange = { enabled ->
                        onAppearanceModeChange(if (enabled) AppearanceMode.Dark else AppearanceMode.Light)
                    },
                )
            }

            SettingsSection(
                title = "Security",
                footer = if (isDeviceAuthSupported(activity)) {
                    "Require biometrics each time you open the app."
                } else {
                    null
                },
            ) {
                if (isDeviceAuthSupported(activity)) {
                    SettingsToggleRow(
                        title = "Biometric unlock",
                        icon = Icons.Outlined.Fingerprint,
                        checked = deviceAuthEnabled,
                        onCheckedChange = { requested ->
                            if (!requested) {
                                authViewModel.setDeviceAuthEnabled(false)
                                return@SettingsToggleRow
                            }
                            scope.launch {
                                val confirmed = activity.authenticateDeviceOwner(
                                    title = "Enable app lock",
                                    subtitle = "Confirm it's you to turn on biometric lock",
                                )
                                if (confirmed) authViewModel.setDeviceAuthEnabled(true)
                            }
                        },
                    )
                    SettingsDivider()
                }
                SettingsRow(
                    title = "Change password",
                    icon = Icons.Outlined.Key,
                    showChevron = true,
                    onClick = { showChangePassword = true },
                )
            }

            SettingsSection(title = "About") {
                ownProfile?.defaultLocation?.takeIf { it.isNotBlank() }?.let { location ->
                    SettingsRow(
                        title = "Default location",
                        icon = Icons.Outlined.LocationOn,
                        value = location,
                    )
                    SettingsDivider()
                }
                SettingsRow(
                    title = "Version",
                    icon = Icons.Outlined.Info,
                    value = BuildConfig.VERSION_NAME,
                )
                SettingsDivider()
                SettingsRow(
                    title = "Privacy Policy",
                    icon = Icons.Outlined.PrivacyTip,
                    showChevron = true,
                    onClick = { showPrivacyPolicy = true },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Terms of Service",
                    icon = Icons.Outlined.Description,
                    showChevron = true,
                    onClick = { showTermsOfService = true },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Contact Support",
                    icon = Icons.Outlined.Email,
                    showChevron = true,
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:$SUPPORT_EMAIL?subject=Rosterra%20support")
                            },
                        )
                    },
                )
            }

            if (!isManager) {
                SettingsSection(
                    title = "Delete account",
                    footer = "Employer-managed accounts. After approval you are locked for 30 days (cancellable by your manager), then login is removed. Payroll/ATO records are retained.",
                ) {
                    DeleteAccountRows(
                        deletion = ownProfile?.deletion,
                        message = deletionState.errorMessage ?: deletionState.successMessage,
                        isWorking = deletionState.isWorking,
                        onRequestDeletion = { showDeleteConfirm = true },
                    )
                }
            }

            SettingsSection {
                SettingsRow(
                    title = "Sign out",
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    destructive = true,
                    onClick = { showSignOutConfirm = true },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(
            title = "Account",
            icon = Icons.Filled.AccountCircle,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    if (isManager) {
                        "You'll need to sign in again to access the manager dashboard."
                    } else {
                        "You'll need to sign in again to access your roster."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    authViewModel.logout()
                }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            },
            shape = MaterialTheme.shapes.large,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Request account deletion?") },
            text = {
                Text(
                    "Your manager will review the request. If approved, your account is locked immediately " +
                        "and sign-in is permanently removed after 30 days. Name, DOB, address, TFN, timesheets " +
                        "and payslips are kept for Australian tax records.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    deletionViewModel.requestDeletion()
                }) { Text("Send request to manager", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            shape = MaterialTheme.shapes.large,
        )
    }

    comingSoonTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { comingSoonTitle = null },
            title = { Text(title) },
            text = { Text("Coming soon — this screen will match the iOS app.") },
            confirmButton = {
                TextButton(onClick = { comingSoonTitle = null }) { Text("OK") }
            },
            shape = MaterialTheme.shapes.large,
        )
    }

    if (showChangePassword) {
        Dialog(
            onDismissRequest = { showChangePassword = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TextButton(onClick = { showChangePassword = false }) {
                        Text("Close")
                    }
                    ChangePasswordScreen(isForced = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ProfileHero(user: AppUser?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(BrandIndigoStrong.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = user?.initials ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = BrandIndigoStrong,
            )
        }
        Text(
            text = user?.fullName?.ifBlank { "—" } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (user != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftTag(
                    text = if (user.role == UserRole.Staff) "Staff" else "Manager",
                    tint = BrandIndigoStrong,
                )
                SoftTag(
                    text = user.status.rawValue.replaceFirstChar { it.uppercase() },
                    tint = if (user.status == UserStatus.Active) AccentEmeraldLight else ErrorRedLight,
                )
                user.employmentType?.label?.let { SoftTag(text = it, tint = TextSecondaryLight) }
            }
        }
    }
}

@Composable
private fun DetailsSection(user: AppUser?, emailVerified: Boolean) {
    SettingsSection {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "EMAIL",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiaryLight,
                        )
                        SoftTag(
                            text = if (emailVerified) "Verified" else "Pending",
                            tint = if (emailVerified) AccentEmeraldLight else WarningAmberLight,
                        )
                    }
                    Text(
                        text = user?.email.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Change email",
                    tint = BrandIndigoStrong,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { /* change-email sheet — Phase 8 */ },
                )
            }

            user?.employeeId?.takeIf { it.isNotBlank() }?.let { employeeId ->
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text("EMPLOYEE ID", style = MaterialTheme.typography.labelMedium, color = TextTertiaryLight)
                Spacer(modifier = Modifier.height(4.dp))
                Text(employeeId, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            user?.memberSince?.let { since ->
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text("MEMBER SINCE", style = MaterialTheme.typography.labelMedium, color = TextTertiaryLight)
                Spacer(modifier = Modifier.height(4.dp))
                Text(since, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StaffStatsSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniStatCard(value = "—", label = "Approved hrs", modifier = Modifier.weight(1f))
        MiniStatCard(value = "0", label = "Timesheets", modifier = Modifier.weight(1f))
        MiniStatCard(value = "0", label = "Pending", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DeleteAccountRows(
    deletion: AccountDeletionState?,
    message: String?,
    isWorking: Boolean,
    onRequestDeletion: () -> Unit,
) {
    when (deletion?.status) {
        AccountDeletionStatus.Requested ->
            SettingsRow(
                title = "Deletion requested — waiting for manager",
                icon = Icons.Outlined.Info,
            )
        AccountDeletionStatus.Approved ->
            SettingsRow(
                title = "Account locked — deletion scheduled",
                icon = Icons.Outlined.Delete,
                destructive = true,
            )
        AccountDeletionStatus.AuthPurged ->
            SettingsRow(
                title = "Account closed — records retained",
                icon = Icons.Outlined.Info,
            )
        AccountDeletionStatus.Cancelled, null ->
            SettingsRow(
                title = "Request account deletion",
                icon = Icons.Outlined.Delete,
                destructive = true,
                onClick = if (!isWorking) onRequestDeletion else null,
            )
    }
    message?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
