package com.surainvestments.roster.ui.manager.staff

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.surainvestments.roster.domain.model.AccountDeletionStatus
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.EmploymentType
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Tfn
import com.surainvestments.roster.domain.model.UserStatus
import com.surainvestments.roster.ui.components.BackPillButton
import com.surainvestments.roster.ui.components.SettingsSection
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.ContentMaxWidth
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors
import java.time.Instant
import java.time.ZoneOffset

/**
 * Manager staff detail / edit sheet — Android analogue of iOS's
 * `ManagerStaffDetailSheet` (Features/Manager/Staff/ManagerStaffView.swift).
 * Same section layout: Details, Tax, Email, Address, Record, Emergency
 * contact. Wage assignment is omitted (Phase 10/Payroll isn't built yet on
 * Android). Lock/unlock, reset-password, and direct change-email are kept
 * under "Manager actions" — Android/PWA-only affordances iOS doesn't have
 * (iOS's Record status picker only ever toggles Active/Inactive).
 */
private data class EditBaseline(
    val fullName: String,
    val phone: String,
    val employeeId: String,
    val tfn: String,
    val employmentType: EmploymentType,
    val status: UserStatus,
    val startDateKey: String?,
    val dobKey: String?,
    val emergencyName: String,
    val emergencyPhone: String,
    val emergencyAddress: String,
    val emergencyEmail: String,
)

@Composable
fun StaffEditSheet(
    staff: AppUser,
    viewModel: StaffViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val staffList by viewModel.staffList.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val liveStaff = staffList.firstOrNull { it.id == staff.id } ?: staff

    var isEditMode by rememberSaveable(staff.id) { mutableStateOf(false) }

    var fullName by rememberSaveable(staff.id) { mutableStateOf(staff.fullName) }
    var phone by rememberSaveable(staff.id) { mutableStateOf(staff.phone ?: "") }
    var employeeId by rememberSaveable(staff.id) { mutableStateOf(staff.employeeId ?: "") }
    var tfnField by rememberSaveable(staff.id) { mutableStateOf(staff.tfn?.let { Tfn.format(it) } ?: "") }
    var employmentType by rememberSaveable(staff.id) { mutableStateOf(staff.employmentType ?: EmploymentType.Casual) }
    var status by rememberSaveable(staff.id) { mutableStateOf(staff.status) }
    var startDateKey by rememberSaveable(staff.id) { mutableStateOf(staff.startDate) }
    var dobKey by rememberSaveable(staff.id) { mutableStateOf(staff.dob) }
    var emergencyName by rememberSaveable(staff.id) { mutableStateOf(staff.emergencyContactName ?: "") }
    var emergencyPhone by rememberSaveable(staff.id) { mutableStateOf(staff.emergencyContactPhone ?: "") }
    var emergencyAddress by rememberSaveable(staff.id) { mutableStateOf(staff.emergencyContactAddress ?: "") }
    var emergencyEmail by rememberSaveable(staff.id) { mutableStateOf(staff.emergencyContactEmail ?: "") }

    val baseline = remember(staff.id) {
        EditBaseline(
            fullName = staff.fullName,
            phone = staff.phone ?: "",
            employeeId = staff.employeeId ?: "",
            tfn = Tfn.normalize(staff.tfn ?: ""),
            employmentType = staff.employmentType ?: EmploymentType.Casual,
            status = staff.status,
            startDateKey = staff.startDate,
            dobKey = staff.dob,
            emergencyName = staff.emergencyContactName ?: "",
            emergencyPhone = staff.emergencyContactPhone ?: "",
            emergencyAddress = staff.emergencyContactAddress ?: "",
            emergencyEmail = staff.emergencyContactEmail ?: "",
        )
    }
    val current = EditBaseline(
        fullName = fullName,
        phone = phone,
        employeeId = employeeId,
        tfn = Tfn.normalize(tfnField),
        employmentType = employmentType,
        status = status,
        startDateKey = startDateKey,
        dobKey = dobKey,
        emergencyName = emergencyName,
        emergencyPhone = emergencyPhone,
        emergencyAddress = emergencyAddress,
        emergencyEmail = emergencyEmail,
    )
    val hasChanges = current != baseline

    var showResetPassword by remember { mutableStateOf(false) }
    var showChangeEmailDirect by remember { mutableStateOf(false) }
    var showAddressConfirm by remember { mutableStateOf(false) }
    var showApproveDeletionConfirm by remember { mutableStateOf(false) }
    var showDeclineDeletionConfirm by remember { mutableStateOf(false) }
    var showCancelDeletionConfirm by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)

    fun save() {
        val trimmedName = fullName.trim()
        if (trimmedName.isEmpty()) {
            validationError = "Name can't be empty."
            return
        }
        val trimmedEmergencyEmail = emergencyEmail.trim()
        if (trimmedEmergencyEmail.isNotEmpty() && !trimmedEmergencyEmail.contains("@")) {
            validationError = "Enter a valid emergency contact email."
            return
        }
        val tfnError = Tfn.validationError(tfnField)
        if (tfnError != null) {
            validationError = tfnError
            return
        }
        validationError = null

        val cleanedEmployeeId = employeeId.uppercase().filter { it.isLetterOrDigit() }
        val cleanedTfn = Tfn.normalize(tfnField)
        val trimmedPhone = phone.trim()
        val trimmedEmergencyName = emergencyName.trim()
        val trimmedEmergencyPhone = emergencyPhone.trim()
        val trimmedEmergencyAddress = emergencyAddress.trim()

        val fields = buildMap<String, Any> {
            if (trimmedName != baseline.fullName) put("fullName", trimmedName)
            if (trimmedPhone != baseline.phone) put("phone", trimmedPhone)
            if (cleanedEmployeeId != baseline.employeeId) put("employeeId", cleanedEmployeeId)
            if (cleanedTfn != baseline.tfn) put("tfn", cleanedTfn)
            if (employmentType != baseline.employmentType) put("employmentType", employmentType.rawValue)
            if (status != baseline.status && staff.status != UserStatus.Locked) put("status", status.rawValue)
            if (startDateKey != baseline.startDateKey) put("startDate", startDateKey ?: "")
            if (dobKey != baseline.dobKey) put("dob", dobKey ?: "")
            if (trimmedEmergencyName != baseline.emergencyName) {
                put("emergencyContactName", trimmedEmergencyName)
                put("emergencyContact", trimmedEmergencyName)
            }
            if (trimmedEmergencyPhone != baseline.emergencyPhone) put("emergencyContactPhone", trimmedEmergencyPhone)
            if (trimmedEmergencyAddress != baseline.emergencyAddress) put("emergencyContactAddress", trimmedEmergencyAddress)
            if (trimmedEmergencyEmail != baseline.emergencyEmail) put("emergencyContactEmail", trimmedEmergencyEmail)
        }

        employeeId = cleanedEmployeeId
        tfnField = if (cleanedTfn.isEmpty()) "" else Tfn.format(cleanedTfn)

        if (fields.isEmpty()) {
            isEditMode = false
            return
        }
        viewModel.updateStaffFields(staff.id, fields) { isEditMode = false }
    }

    fun handlePrimaryAction() {
        if (isEditMode) {
            if (hasChanges) save() else isEditMode = false
        } else {
            isEditMode = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackPillButton(onClick = onClose)
            Text(
                text = staff.fullName.ifBlank { staff.email },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (uiState.isWorking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = ::handlePrimaryAction) {
                    Text(if (isEditMode && hasChanges) "Save" else if (isEditMode) "Done" else "Edit")
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(LocalNavBarPadding.current)
                .padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                validationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                SettingsSection(title = "Details") {
                    EditableFieldRow("Full name", fullName, { fullName = it }, isEditMode, capitalization = KeyboardCapitalization.Words)
                    RowDivider()
                    EditableFieldRow("Phone", phone, { phone = it }, isEditMode, keyboardType = KeyboardType.Phone)
                    RowDivider()
                    EditableFieldRow(
                        "Employee ID",
                        employeeId,
                        { employeeId = it },
                        isEditMode,
                        capitalization = KeyboardCapitalization.Characters,
                    )
                    RowDivider()
                    EditablePickerRow(
                        label = "Employment",
                        selected = employmentType,
                        options = EmploymentType.entries,
                        optionLabel = { it.label },
                        onSelect = { employmentType = it },
                        isEditMode = isEditMode,
                    )
                }

                SettingsSection(
                    title = "Tax",
                    footer = "Manager-only. Full TFN is retained for ATO / payroll after account closure.",
                ) {
                    if (isEditMode) {
                        EditableFieldRow(
                            "TFN",
                            tfnField,
                            { tfnField = Tfn.format(it) },
                            isEditMode = true,
                            keyboardType = KeyboardType.Number,
                        )
                    } else {
                        ReadOnlyFieldRow("TFN", Tfn.mask(staff.tfn))
                    }
                }

                EmailSection(staff = staff, liveStaff = liveStaff, viewModel = viewModel, onDirectChange = { showChangeEmailDirect = true })

                SettingsSection(title = "Address") {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = staff.address?.takeIf { it.isNotBlank() } ?: "No address on file",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showAddressConfirm = true }) {
                            Text("Require new address on next login", color = StatusColors.Pending)
                        }
                    }
                }

                SettingsSection(title = "Record") {
                    if (liveStaff.status == UserStatus.Locked) {
                        ReadOnlyFieldRow("Status", "Locked")
                    } else {
                        EditablePickerRow(
                            label = "Status",
                            selected = status,
                            options = listOf(UserStatus.Active, UserStatus.Inactive),
                            optionLabel = { it.rawValue.replaceFirstChar(Char::uppercase) },
                            onSelect = { status = it },
                            isEditMode = isEditMode,
                        )
                    }
                    RowDivider()
                    ReadOnlyFieldRow("Member since", staff.memberSince ?: "—")
                    RowDivider()
                    EditableDateRow("Start date", startDateKey, { startDateKey = it }, isEditMode)
                    RowDivider()
                    EditableDateRow("Date of birth", dobKey, { dobKey = it }, isEditMode)
                }

                SettingsSection(title = "Emergency contact") {
                    EditableFieldRow("Name", emergencyName, { emergencyName = it }, isEditMode, capitalization = KeyboardCapitalization.Words)
                    RowDivider()
                    EditableFieldRow("Phone", emergencyPhone, { emergencyPhone = it }, isEditMode, keyboardType = KeyboardType.Phone)
                    RowDivider()
                    EditableFieldRow(
                        "Address",
                        emergencyAddress,
                        { emergencyAddress = it },
                        isEditMode,
                        capitalization = KeyboardCapitalization.Words,
                    )
                    RowDivider()
                    EditableFieldRow(
                        "Email",
                        emergencyEmail,
                        { emergencyEmail = it },
                        isEditMode,
                        keyboardType = KeyboardType.Email,
                        capitalization = KeyboardCapitalization.None,
                    )
                }

                ManagerActionsSection(
                    staff = staff,
                    liveStaff = liveStaff,
                    viewModel = viewModel,
                    onResetPassword = { showResetPassword = true },
                    onChangeEmailDirect = { showChangeEmailDirect = true },
                )

                DangerZoneSection(
                    staff = staff,
                    liveStaff = liveStaff,
                    isWorking = uiState.isWorking,
                    onApprove = { showApproveDeletionConfirm = true },
                    onDecline = { showDeclineDeletionConfirm = true },
                    onCancel = { showCancelDeletionConfirm = true },
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showResetPassword) {
        ResetPasswordDialog(
            isWorking = uiState.isWorking,
            onDismiss = { showResetPassword = false },
            onSubmit = { newPassword -> viewModel.resetPassword(staff.id, newPassword) { showResetPassword = false } },
        )
    }
    if (showChangeEmailDirect) {
        ChangeEmailDialog(
            isWorking = uiState.isWorking,
            onDismiss = { showChangeEmailDirect = false },
            onSubmit = { newEmail, managerPassword ->
                viewModel.changeEmail(staff.id, newEmail, managerPassword) { showChangeEmailDirect = false }
            },
        )
    }
    if (showAddressConfirm) {
        AlertDialog(
            onDismissRequest = { showAddressConfirm = false },
            title = { Text("Require new address?") },
            text = {
                Text(
                    "${staff.fullName.ifBlank { "This staff member" }} will be asked to enter a new address " +
                        "next time they open the app, and won't reach their dashboard until they do.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddressConfirm = false
                    viewModel.requireNewAddress(staff.id) {}
                }) { Text("Require new address", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showAddressConfirm = false }) { Text("Cancel") } },
        )
    }
    if (showApproveDeletionConfirm) {
        AlertDialog(
            onDismissRequest = { showApproveDeletionConfirm = false },
            title = { Text("Schedule account deletion?") },
            text = {
                Text(
                    "${staff.fullName.ifBlank { "This staff member" }} will be locked immediately. After 30 days " +
                        "their sign-in is removed. Timesheets and payslips are kept for ATO/payroll. You can cancel within 30 days.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showApproveDeletionConfirm = false
                    viewModel.approveDeletion(staff.id) {}
                }) { Text("Lock & schedule", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showApproveDeletionConfirm = false }) { Text("Cancel") } },
        )
    }
    if (showDeclineDeletionConfirm) {
        AlertDialog(
            onDismissRequest = { showDeclineDeletionConfirm = false },
            title = { Text("Decline deletion request?") },
            text = { Text("${staff.fullName.ifBlank { "This staff member" }} stays active and can use the app.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeclineDeletionConfirm = false
                    viewModel.declineDeletion(staff.id) {}
                }) { Text("Decline request") }
            },
            dismissButton = { TextButton(onClick = { showDeclineDeletionConfirm = false }) { Text("Cancel") } },
        )
    }
    if (showCancelDeletionConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelDeletionConfirm = false },
            title = { Text("Cancel scheduled deletion?") },
            text = { Text("Unlocks ${staff.fullName.ifBlank { "this staff member" }} and cancels the 30-day Auth purge.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDeletionConfirm = false
                    viewModel.cancelDeletion(staff.id) {}
                }) { Text("Reinstate account") }
            },
            dismissButton = { TextButton(onClick = { showCancelDeletionConfirm = false }) { Text("Keep deletion") } },
        )
    }
}

@Composable
private fun EmailSection(
    staff: AppUser,
    liveStaff: AppUser,
    viewModel: StaffViewModel,
    onDirectChange: () -> Unit,
) {
    SettingsSection(
        title = "Email",
        footer = "Email is a sign-in credential, so ${staff.fullName.ifBlank { "the staff member" }} changes it " +
            "themselves. They'll be prompted in the app and confirm via a Firebase verification link.",
    ) {
        ReadOnlyFieldRow("Email", staff.email)
        RowDivider()
        if (liveStaff.emailChangeRequired) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Change requested — waiting for them to update it",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusColors.Pending,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.cancelEmailChangeRequest(staff.id) {} }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            TextButton(
                onClick = { viewModel.requestEmailChange(staff.id) {} },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ask ${staff.fullName.ifBlank { "them" }} to change their email")
            }
        }
    }
}

@Composable
private fun ManagerActionsSection(
    staff: AppUser,
    liveStaff: AppUser,
    viewModel: StaffViewModel,
    onResetPassword: () -> Unit,
    onChangeEmailDirect: () -> Unit,
) {
    val locked = liveStaff.status == UserStatus.Locked
    SettingsSection(title = "Manager actions") {
        ActionRow(
            icon = if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock,
            label = if (locked) "Unlock account" else "Lock account",
            onClick = {
                val target = if (locked) UserStatus.Active else UserStatus.Locked
                viewModel.setStatus(staff.id, target) {}
            },
        )
        RowDivider()
        ActionRow(icon = Icons.Filled.Password, label = "Reset password", onClick = onResetPassword)
        RowDivider()
        ActionRow(icon = Icons.Filled.Email, label = "Change email directly", onClick = onChangeEmailDirect)
    }
}

@Composable
private fun DangerZoneSection(
    staff: AppUser,
    liveStaff: AppUser,
    isWorking: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
) {
    SettingsSection(
        title = "Account deletion",
        footer = "ATO-safe: locks the account, then removes sign-in after 30 days. Timesheets, payslips, name, " +
            "DOB, address and TFN are retained.",
    ) {
        val deletion = liveStaff.deletion
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            when (deletion?.status) {
                AccountDeletionStatus.AuthPurged -> Text(
                    "Former staff — login removed; records retained.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AccountDeletionStatus.Approved -> {
                    Text(
                        "Locked. Auth purge scheduled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onCancel, enabled = !isWorking) { Text("Cancel deletion & reinstate") }
                }
                AccountDeletionStatus.Requested -> {
                    Text(
                        "Staff requested deletion.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusColors.Pending,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onApprove, enabled = !isWorking) {
                            Text("Approve — lock 30 days", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = onDecline, enabled = !isWorking) { Text("Decline") }
                    }
                }
                else -> {
                    TextButton(onClick = onApprove, enabled = !isWorking) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Schedule account deletion", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun ReadOnlyFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun EditableFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEditMode: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isEditMode) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun <T> EditablePickerRow(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    isEditMode: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (isEditMode) {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(optionLabel(selected))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(optionLabel(option)) },
                            onClick = { onSelect(option); expanded = false },
                        )
                    }
                }
            }
        } else {
            Text(
                optionLabel(selected),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableDateRow(
    label: String,
    dateKey: String?,
    onDateChange: (String?) -> Unit,
    isEditMode: Boolean,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val display = dateKey?.let { RosterFormat.dateShort(it) } ?: "—"
        if (isEditMode) {
            TextButton(onClick = { showPicker = true }) { Text(if (dateKey != null) display else "Set date") }
        } else {
            Text(display, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }

    if (showPicker) {
        val initialMillis = dateKey?.let { RosterCalendar.parseDateKey(it) }
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
