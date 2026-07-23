package com.surainvestments.roster.ui.staff.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CoffeeMaker
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.ClockSession
import com.surainvestments.roster.domain.model.Fix
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.SecondaryButton
import com.surainvestments.roster.ui.theme.BrandIndigoStrong
import com.surainvestments.roster.ui.theme.StatusColors
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface ClockDialog {
    data object EndConfirm : ClockDialog
    data object EndChoice : ClockDialog
    data object EarlyLeaveNote : ClockDialog
    data class GeofencePrompt(val fix: Fix?, val message: String, val confirmLabel: String) : ClockDialog
    data class Blocked(val message: String) : ClockDialog
    data class ErrorAlert(val message: String) : ClockDialog
}

/**
 * Start Shift / break / End Shift controls for [shift], shown inline on Home's today section
 * only while [com.surainvestments.roster.domain.model.ClockSession.isClockable]-equivalent gating
 * (no timesheet yet, or an active/ended session for this shift) holds — that check lives in the
 * caller. Mirrors iOS `ClockInCard`.
 */
@Composable
fun ClockInCard(
    shift: Shift,
    onSubmitHours: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClockInViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isWorking by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ClockDialog?>(null) }
    var earlyLeaveNote by remember { mutableStateOf("") }
    var pendingUseRosteredEnd by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }

    LaunchedEffect(session?.isActive) {
        while (true) {
            now = viewModel.serverNow()
            delay(if (session?.isActive == true) 1_000 else 5_000)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    fun ensurePermissionThen(action: () -> Unit) {
        if (viewModel.hasLocationPermission()) {
            action()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            action() // proceed regardless -- LocationService reports "denied" gracefully if still unauthorized
        }
    }

    fun runStart() {
        ensurePermissionThen {
            scope.launch {
                isWorking = true
                when (val result = viewModel.attemptStart(shift)) {
                    is ClockAttemptResult.Committed -> Unit
                    is ClockAttemptResult.NeedsConfirmation -> dialog = ClockDialog.GeofencePrompt(result.fix, result.message, result.confirmLabel)
                    is ClockAttemptResult.Blocked -> dialog = ClockDialog.Blocked(result.message)
                    is ClockAttemptResult.Failed -> dialog = ClockDialog.ErrorAlert(result.message)
                }
                isWorking = false
            }
        }
    }

    fun runEnd(note: String?, useRosteredEnd: Boolean) {
        ensurePermissionThen {
            scope.launch {
                isWorking = true
                when (val result = viewModel.endShift(shift, note, useRosteredEnd)) {
                    is ClockAttemptResult.Committed -> Unit
                    is ClockAttemptResult.Failed -> dialog = ClockDialog.ErrorAlert(result.message)
                    else -> Unit
                }
                isWorking = false
            }
        }
    }

    val activeSession = session?.takeIf { it.shiftId == shift.id }
    val shiftEnded = now.isAfter(shift.endDateTime)

    RosterCard(modifier = modifier) {
        when {
            activeSession == null && shiftEnded -> MissedBody(shift = shift, now = now, onSubmitHours = onSubmitHours)
            activeSession == null -> IdleBody(shift = shift, now = now, isWorking = isWorking, onStart = { dialog = null; runStart() })
            activeSession.isActive -> ActiveBody(
                session = activeSession,
                shift = shift,
                now = now,
                isWorking = isWorking,
                onToggleBreak = { if (activeSession.isOnBreak) viewModel.endBreak() else viewModel.startBreak() },
                onEndTap = { dialog = ClockDialog.EndConfirm },
            )
            else -> EndedBody(session = activeSession, shift = shift, now = now, onSubmitHours = onSubmitHours)
        }
    }

    when (val d = dialog) {
        ClockDialog.EndConfirm -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Are you sure you want to end your shift?") },
            text = {
                Text(
                    if (viewModel.serverNow().isBefore(shift.endDateTime)) {
                        "Your rostered shift runs until ${RosterFormat.timeOfDay(shift.rosteredEnd)}. Ending now can't be undone."
                    } else {
                        "Ending your shift can't be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = if (viewModel.serverNow().isBefore(shift.endDateTime)) ClockDialog.EarlyLeaveNote else ClockDialog.EndChoice
                }) { Text("End Shift", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Keep Working") } },
        )
        ClockDialog.EndChoice -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("How did your shift finish?") },
            text = { Text("\"Stayed back\" records your actual finish time — you can adjust the hours before submitting. Otherwise your rostered end time is used.") },
            confirmButton = {
                TextButton(onClick = { dialog = null; pendingUseRosteredEnd = false; runEnd(null, false) }) { Text("Stayed back") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { dialog = null; runEnd(null, true) }) { Text("Finished at ${RosterFormat.timeOfDay(shift.rosteredEnd)}") }
                    TextButton(onClick = { dialog = null }) { Text("Cancel") }
                }
            },
        )
        ClockDialog.EarlyLeaveNote -> AlertDialog(
            onDismissRequest = { dialog = null; earlyLeaveNote = "" },
            title = { Text("Leaving early?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add a short note for your manager about why you're ending your shift early (optional).")
                    OutlinedTextField(value = earlyLeaveNote, onValueChange = { earlyLeaveNote = it }, placeholder = { Text("E.g. feeling unwell") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val note = earlyLeaveNote
                    dialog = null; earlyLeaveNote = ""
                    runEnd(note, false)
                }) { Text("End Shift") }
            },
            dismissButton = { TextButton(onClick = { dialog = null; earlyLeaveNote = "" }) { Text("Cancel") } },
        )
        is ClockDialog.GeofencePrompt -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Location check") },
            text = { Text(d.message) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch {
                        isWorking = true
                        when (val result = viewModel.confirmStart(shift, d.fix)) {
                            is ClockAttemptResult.Failed -> dialog = ClockDialog.ErrorAlert(result.message)
                            else -> Unit
                        }
                        isWorking = false
                    }
                }) { Text(d.confirmLabel) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        is ClockDialog.Blocked -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("You are outside the work zone") },
            text = { Text(d.message) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
        )
        is ClockDialog.ErrorAlert -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Couldn't sync your shift") },
            text = { Text(d.message) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
        )
        null -> Unit
    }
}

/** Shown once the shift's rostered end has passed with no clock-in at all — starting now would produce a meaningless near-zero session, so skip straight to manual submission. */
@Composable
private fun MissedBody(shift: Shift, now: Instant, onSubmitHours: () -> Unit) {
    val submittable = shift.isSubmittable(now)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "Missed clocking in", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                text = if (submittable) {
                    "This shift ended without a clock-in. You can still submit your hours directly."
                } else {
                    "This shift ended without a clock-in. Hours can be submitted after ${RosterFormat.timeOfDay(shift.rosteredEnd)}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryButton(text = "Submit hours", onClick = onSubmitHours, enabled = submittable, fullWidth = false)
    }
}

@Composable
private fun IdleBody(shift: Shift, now: Instant, isWorking: Boolean, onStart: () -> Unit) {
    val unlockAt = shift.startDateTime.minusSeconds(BusinessRules.earlyClockInWindowSeconds.toLong())
    val unlocked = !now.isBefore(unlockAt)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (unlocked) "Ready to start?" else "Starts soon",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = if (unlocked) {
                    if (now.isBefore(shift.startDateTime)) {
                        "Early check-in is open — paid time starts ${RosterFormat.timeOfDay(shift.rosteredStart)}."
                    } else {
                        "Clock in when your shift begins."
                    }
                } else {
                    "Start Shift unlocks at ${RosterFormat.timeOfDay(hhmm(unlockAt))} — 5 min before your shift."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unlocked) {
            PrimaryButton(text = "Start Shift", onClick = onStart, loading = isWorking, fullWidth = false, leadingIcon = Icons.Filled.PlayArrow)
        } else {
            Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActiveBody(
    session: ClockSession,
    shift: Shift,
    now: Instant,
    isWorking: Boolean,
    onToggleBreak: () -> Unit,
    onEndTap: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (session.isOnBreak) StatusColors.Pending else StatusColors.Approved, CircleShape),
                    )
                    Text(
                        text = if (session.isOnBreak) "On break" else "On the clock",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Text(
                    text = if (session.clockInAt.isBefore(shift.startDateTime)) {
                        "Checked in ${RosterFormat.timeOfDay(hhmm(session.clockInAt))} · paid from ${RosterFormat.timeOfDay(shift.rosteredStart)}"
                    } else {
                        "Started ${RosterFormat.timeOfDay(hhmm(session.clockInAt))}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = elapsed(session.paidWorkedSeconds(shift.startDateTime, now)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                val breakSecs = session.totalBreakSeconds(now)
                if (breakSecs >= 60) {
                    Text(text = "${breakSecs / 60}m break", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                text = if (session.isOnBreak) "End Break" else "Start Break",
                onClick = onToggleBreak,
                enabled = !isWorking,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(text = "End Shift", onClick = onEndTap, loading = isWorking, enabled = !isWorking, leadingIcon = Icons.Filled.Stop, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EndedBody(session: ClockSession, shift: Shift, now: Instant, onSubmitHours: () -> Unit) {
    val submittable = shift.isSubmittable(now)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "Shift ended", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            val breakMinutes = session.timesheetBreakMinutes()
            Text(
                text = "${RosterFormat.decimalHours(session.paidWorkedSeconds(shift.startDateTime) / 3600.0)}h worked · " +
                    if (breakMinutes > 0) "${breakMinutes}m break" else "no break",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!submittable) {
                Text(
                    text = "Hours can be submitted after ${RosterFormat.timeOfDay(shift.rosteredEnd)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PrimaryButton(text = "Submit hours", onClick = onSubmitHours, enabled = submittable, fullWidth = false)
    }
}

private fun hhmm(instant: Instant): String =
    java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        .withZone(com.surainvestments.roster.domain.model.RosterCalendar.zoneId)
        .format(instant)

private fun elapsed(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%d:%02d:%02d".format(h, m, s)
}
