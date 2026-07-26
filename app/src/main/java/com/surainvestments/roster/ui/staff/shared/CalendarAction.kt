package com.surainvestments.roster.ui.staff.shared

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.surainvestments.roster.data.repository.AppSettingsRepository
import com.surainvestments.roster.data.service.CalendarResult
import com.surainvestments.roster.data.service.CalendarService
import com.surainvestments.roster.domain.model.Shift
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@HiltViewModel
class CalendarActionViewModel @Inject constructor(
    private val calendarService: CalendarService,
    appSettingsRepository: AppSettingsRepository,
) : ViewModel() {
    val companyName: StateFlow<String> = appSettingsRepository.appSettings
        .map { it.companyName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Rosterra")

    fun hasPermission(): Boolean = calendarService.hasPermission()

    suspend fun addToCalendar(shift: Shift): CalendarResult = calendarService.addShift(shift, companyName.value)
}

/**
 * Returns an `(Shift) -> Unit` action for an "Add to Calendar" tap — requests **both**
 * `WRITE_CALENDAR` and `READ_CALENDAR` if not yet granted (the service's `defaultCalendarId`
 * lookup queries the Calendars table, which needs the read permission too — requesting only
 * write left that query throwing and silently falling back to the ICS-share/error path even
 * when the user had just granted calendar access), then proceeds regardless of the outcome (the
 * service falls back to an `.ics` share when access is still denied). Shared by Home and
 * Roster's shift cards so both wire the exact same permission/fallback flow.
 */
@Composable
fun rememberAddToCalendarAction(viewModel: CalendarActionViewModel = hiltViewModel()): (Shift) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingShift by remember { mutableStateOf<Shift?>(null) }

    fun runAdd(shift: Shift) {
        scope.launch {
            when (val result = viewModel.addToCalendar(shift)) {
                CalendarResult.Added -> Toast.makeText(context, "Added to Calendar", Toast.LENGTH_SHORT).show()
                is CalendarResult.SharedFile -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/calendar"
                        putExtra(Intent.EXTRA_STREAM, result.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share calendar event"))
                }
                is CalendarResult.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pendingShift?.let(::runAdd)
        pendingShift = null
    }

    return { shift ->
        if (viewModel.hasPermission()) {
            runAdd(shift)
        } else {
            pendingShift = shift
            permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR))
        }
    }
}
