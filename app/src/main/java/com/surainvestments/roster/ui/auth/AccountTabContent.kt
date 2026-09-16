package com.surainvestments.roster.ui.auth

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.Bolt
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
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.surainvestments.roster.BuildConfig
import com.surainvestments.roster.data.local.AppearanceMode
import com.surainvestments.roster.domain.model.AccountDeletionStatus
import com.surainvestments.roster.domain.model.AccountDeletionState
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.UserRole
import com.surainvestments.roster.domain.model.UserStatus
import com.surainvestments.roster.ui.components.HapticEvent
import com.surainvestments.roster.ui.components.Haptics
import com.surainvestments.roster.ui.components.MiniStatCard
import com.surainvestments.roster.ui.components.PasswordField
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
import com.surainvestments.roster.ui.screens.NotificationSettingsScreen
import com.surainvestments.roster.ui.screens.PrivacyPolicyScreen
import com.surainvestments.roster.ui.screens.TermsOfServiceScreen
import com.surainvestments.roster.ui.screens.VersionHistoryScreen
import com.surainvestments.roster.ui.staff.payslips.PayslipsScreen
import com.surainvestments.roster.ui.theme.AccentEmeraldLight
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import com.surainvestments.roster.ui.theme.ErrorRedLight
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.TextSecondaryLight
import com.surainvestments.roster.ui.theme.TextTertiaryLight
import com.surainvestments.roster.ui.theme.WarningAmberLight
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val haptics = LocalHapticFeedback.current
    val deviceAuthEnabled by authViewModel.isDeviceAuthEnabled.collectAsState()
    val quickLoginEnabled by authViewModel.isQuickLoginEnabled.collectAsState()
    val ownProfile by authViewModel.ownProfile.collectAsState()
    val deletionState by deletionViewModel.uiState.collectAsState()
    val isManager = ownProfile?.role == UserRole.Manager

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showStaffDirectory by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showChangeEmail by remember { mutableStateOf(false) }
    var showTermsOfService by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showPayslips by remember { mutableStateOf(false) }
    var showVersionHistory by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    var showEnableQuickLogin by remember { mutableStateOf(false) }
    var emailVerified by remember { mutableStateOf(false) }
    var comingSoonTitle by remember { mutableStateOf<String?>(null) }

    // NotificationManagerCompat.areNotificationsEnabled() reflects the real, user-visible state
    // across every API level (the POST_NOTIFICATIONS runtime permission on 33+, and the
    // system-settings toggle below that) — re-checked on resume so flipping it in "System
    // notification settings" and coming back updates this row immediately.
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    if (showEditProfile) {
        EditProfileScreen(
            onBack = { showEditProfile = false },
            onSaved = { showEditProfile = false },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    if (showVersionHistory) {
        VersionHistoryScreen(
            onBack = { showVersionHistory = false },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    if (showNotificationSettings) {
        NotificationSettingsScreen(
            isManager = isManager,
            onBack = { showNotificationSettings = false },
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

            ProfileHero(user = ownProfile, authViewModel = authViewModel, onEditProfile = { showEditProfile = true })

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

            DetailsSection(user = ownProfile, emailVerified = emailVerified, onChangeEmail = { showChangeEmail = true })

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

            SettingsSection(title = "Notifications") {
                SettingsRow(
                    title = "Notifications",
                    icon = Icons.Outlined.Notifications,
                    value = if (notificationsEnabled) "On" else "Off",
                    valueColor = if (notificationsEnabled) AccentEmeraldLight else TextTertiaryLight,
                    showChevron = true,
                    onClick = { showNotificationSettings = true },
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
                if (isStrongBiometricSupported(activity)) {
                    SettingsToggleRow(
                        title = "Quick Login",
                        icon = Icons.Outlined.Bolt,
                        checked = quickLoginEnabled,
                        onCheckedChange = { requested ->
                            if (!requested) {
                                authViewModel.disableQuickLogin()
                            } else {
                                showEnableQuickLogin = true
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
                // Matches iOS exactly: staff's AccountView renders this as a plain, non-navigable
                // row; manager's ManagerAccountView wraps the identical row in a NavigationLink to
                // AppVersionHistoryView. Same row, tap-through gated by role — not two variants.
                SettingsRow(
                    title = "Version",
                    icon = Icons.Outlined.Info,
                    value = BuildConfig.VERSION_NAME,
                    showChevron = isManager,
                    onClick = if (isManager) ({ showVersionHistory = true }) else null,
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

    if (showEnableQuickLogin) {
        EnableQuickLoginDialog(
            authViewModel = authViewModel,
            activity = activity,
            scope = scope,
            onDismiss = { showEnableQuickLogin = false },
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
                    Haptics.perform(haptics, HapticEvent.SignOut)
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

    if (showChangeEmail) {
        Dialog(
            onDismissRequest = { showChangeEmail = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TextButton(onClick = { showChangeEmail = false }) {
                        Text("Close")
                    }
                    ChangeEmailScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ProfileHero(user: AppUser?, authViewModel: AuthViewModel, onEditProfile: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoFile by authViewModel.profilePhotoFile.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    // Decoded once per file change, not on every recomposition — profile photos are small
    // (ImageCompressor's 2MB/1600px budget) so a synchronous decode here is cheap.
    val photoBitmap = remember(photoFile) { photoFile?.let { BitmapFactory.decodeFile(it.path) } }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                }
                bitmap?.let(authViewModel::setProfilePhoto)
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createProfilePhotoCaptureUri(context)
            pendingCaptureUri = uri
            takePictureLauncher.launch(uri)
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                }
                bitmap?.let(authViewModel::setProfilePhoto)
            }
        }
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createProfilePhotoCaptureUri(context)
            pendingCaptureUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(BrandIndigoStrong.copy(alpha = 0.12f))
                    .clickable { menuExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap.asImageBitmap(),
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = user?.initials ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BrandIndigoStrong,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { menuExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "Change photo",
                    tint = BrandIndigoStrong,
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Take Photo") },
                    onClick = { menuExpanded = false; openCamera() },
                )
                DropdownMenuItem(
                    text = { Text("Choose from Gallery") },
                    onClick = {
                        menuExpanded = false
                        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                if (photoFile != null) {
                    DropdownMenuItem(
                        text = { Text("Remove Photo") },
                        onClick = {
                            menuExpanded = false
                            authViewModel.removeProfilePhoto()
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = user?.fullName?.ifBlank { "—" } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Edit profile",
                tint = BrandIndigoStrong,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onEditProfile),
            )
        }
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

private fun createProfilePhotoCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "profile_photo_captures").apply { mkdirs() }
    val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Confirms the account's real password (via Firebase reauthenticate, never accepted on faith)
 * before the biometric-gated Keystore encrypt step that actually seeds the quick-login store —
 * two independent checks, matching how security-sensitive this credential is.
 */
@Composable
private fun EnableQuickLoginDialog(
    authViewModel: AuthViewModel,
    activity: FragmentActivity,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("Enable Quick Login") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Confirm your password once to enable biometric sign-in on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "Password",
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotBlank() && !isWorking,
                onClick = {
                    isWorking = true
                    error = null
                    scope.launch {
                        if (!authViewModel.verifyPasswordForQuickLogin(password)) {
                            Haptics.perform(haptics, HapticEvent.SaveError)
                            error = "That password doesn't match your account."
                            isWorking = false
                            return@launch
                        }
                        val cipher = authViewModel.quickLoginEncryptCipher()
                        val authorizedCipher = activity.authenticateWithCryptoObject(
                            title = "Enable Quick Login",
                            subtitle = "Confirm it's you to enable biometric sign-in",
                            cipher = cipher,
                        )
                        isWorking = false
                        if (authorizedCipher != null) {
                            authViewModel.finishEnablingQuickLogin(password, authorizedCipher)
                            Haptics.perform(haptics, HapticEvent.SaveSuccess)
                            onDismiss()
                        } else {
                            Haptics.perform(haptics, HapticEvent.SaveError)
                            error = "Biometric confirmation was cancelled or failed."
                        }
                    }
                },
            ) { Text(if (isWorking) "Verifying…" else "Continue") }
        },
        dismissButton = {
            TextButton(enabled = !isWorking, onClick = onDismiss) { Text("Cancel") }
        },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun DetailsSection(user: AppUser?, emailVerified: Boolean, onChangeEmail: () -> Unit) {
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
                        .clickable(onClick = onChangeEmail),
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
private fun StaffStatsSection(statsViewModel: AccountStatsViewModel = hiltViewModel()) {
    val stats by statsViewModel.uiState.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniStatCard(value = RosterFormat.decimalHours(stats.approvedHours), label = "Approved hrs", modifier = Modifier.weight(1f))
        MiniStatCard(value = stats.timesheetCount.toString(), label = "Timesheets", modifier = Modifier.weight(1f))
        MiniStatCard(value = stats.pendingCount.toString(), label = "Pending", modifier = Modifier.weight(1f))
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
