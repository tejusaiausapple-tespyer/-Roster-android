package com.surainvestments.roster.ui.staff.roster

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.domain.model.friendlyMessage
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.HapticEvent
import com.surainvestments.roster.ui.components.Haptics
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors
import kotlinx.coroutines.launch

/** Full-screen sheet for reporting an absence on [shift]. Mirrors iOS `ReportAbsenceSheet`. */
@Composable
fun ReportAbsenceSheet(
    shift: Shift,
    existing: Timesheet?,
    onDismiss: () -> Unit,
    onReported: () -> Unit,
    viewModel: ReportAbsenceViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val absenceTint = StatusColors.Absent

    BackHandler(onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = ScreenPillTopBarHeight + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            RosterCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PersonOff,
                        contentDescription = null,
                        tint = absenceTint,
                        modifier = Modifier.padding(end = 14.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(text = "Report absence", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${RosterFormat.dayHeader(shift.date)} · ${RosterFormat.timeOfDay(shift.rosteredStart)}–${RosterFormat.timeOfDay(shift.rosteredEnd)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            RosterCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "REASON (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Let your manager know why") },
                        minLines = 3,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }

            Text(
                text = "This tells your manager you didn't attend this shift. You can undo it until they confirm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            errorMessage?.let { Banner(kind = BannerKind.Error, title = it) }

            PrimaryButton(
                text = "Report absence",
                loading = isWorking,
                enabled = !isWorking,
                onClick = {
                    errorMessage = null
                    scope.launch {
                        isWorking = true
                        try {
                            viewModel.reportAbsence(shift, existing, reason)
                            Haptics.perform(haptics, HapticEvent.SubmitSuccess)
                            onReported()
                        } catch (e: Exception) {
                            Haptics.perform(haptics, HapticEvent.SubmitError)
                            errorMessage = friendlyMessage(e, "Something went wrong.")
                        } finally {
                            isWorking = false
                        }
                    }
                },
            )
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(title = "Absence", onBack = onDismiss, modifier = Modifier.align(Alignment.TopCenter))
    }
}
