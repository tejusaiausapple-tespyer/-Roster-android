package com.surainvestments.roster.ui.manager.staff

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.UserStatus
import com.surainvestments.roster.ui.components.EmptyState
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.StatusPill
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.navigation.LocalNavBarPadding
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors

@Composable
fun StaffListScreen(
    onBack: () -> Unit,
    viewModel: StaffViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val staff by viewModel.staffList.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddStaff by remember { mutableStateOf(false) }
    var selectedStaff by remember { mutableStateOf<AppUser?>(null) }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        (uiState.errorMessage ?: uiState.successMessage)?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    selectedStaff?.let { member ->
        StaffEditSheet(
            staff = member,
            viewModel = viewModel,
            onClose = { selectedStaff = null },
            modifier = modifier,
        )
        return
    }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (staff.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = ScreenPillTopBarHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Outlined.PersonAdd,
                        title = "No staff yet",
                        message = "Add your first team member to start building rosters.",
                        actionTitle = "Add staff",
                        onAction = { showAddStaff = true },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        top = ScreenPillTopBarHeight + 8.dp,
                        bottom = LocalNavBarPadding.current.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(staff, key = { it.id }) { member ->
                        StaffRow(member = member, onClick = { selectedStaff = member })
                    }
                }
            }

            TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
            ScreenPillTopBar(
                title = "Staff",
                icon = Icons.Outlined.Group,
                onBack = onBack,
                onAdd = { showAddStaff = true },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (showAddStaff) {
        AddStaffDialog(
            isWorking = uiState.isWorking,
            onDismiss = { showAddStaff = false },
            onSubmit = { fullName, email, password, employmentType, phone ->
                viewModel.createStaff(fullName, email, password, employmentType, phone, null) {
                    showAddStaff = false
                }
            },
        )
    }
}

@Composable
private fun StaffRow(member: AppUser, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val initials = remember(member.fullName, member.email) {
        member.fullName
            .split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { member.email.take(1).uppercase() }
    }

    RosterCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.fullName.ifBlank { member.email },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = member.employmentType?.label ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            UserStatusPill(status = member.status)
        }
    }
}

@Composable
private fun UserStatusPill(status: UserStatus, modifier: Modifier = Modifier) {
    val (label, tint) = when (status) {
        UserStatus.Active -> "Active" to StatusColors.Active
        UserStatus.Locked -> "Locked" to StatusColors.Locked
        UserStatus.Inactive -> "Inactive" to StatusColors.Inactive
    }
    StatusPill(label = label, tint = tint, modifier = modifier)
}

