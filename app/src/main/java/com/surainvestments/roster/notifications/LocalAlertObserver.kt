package com.surainvestments.roster.notifications

import android.content.Context
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.ShiftRepository
import com.surainvestments.roster.data.repository.TimesheetRepository
import com.surainvestments.roster.domain.model.FcmEventRouting
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.ShiftStatus
import com.surainvestments.roster.domain.model.TimesheetStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Fires a **local** notification immediately on live events occurring while the app is running —
 * a timesheet decision (approved/rejected) and a newly published shift.
 *
 * **Primed on initial session start**: Existing shifts and timesheets loaded when the app starts,
 * signs in, or reinstalls are recorded as initial state during a brief priming window and NEVER
 * trigger notifications. Only actual live transitions occurring after priming post a local alert.
 */
@Singleton
class LocalAlertObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val shiftRepository: ShiftRepository,
    private val timesheetRepository: TimesheetRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        appScope.launch {
            authRepository.authStateFlow().collect { uid ->
                if (uid == null) return@collect

                val primedPublishedShiftIds = mutableSetOf<String>()
                val primedTimesheetStatuses = mutableMapOf<String, TimesheetStatus>()
                var isPrimed = false

                val primingJob = launch {
                    delay(2000)
                    isPrimed = true
                }

                try {
                    combine(
                        shiftRepository.staffShiftsWindow(uid),
                        timesheetRepository.staffTimesheetsByShiftId(uid),
                    ) { shifts, timesheets -> shifts to timesheets }
                        .collect { (shifts, timesheetsByShiftId) ->
                            val published = shifts.filter { it.status == ShiftStatus.Published }.associateBy { it.id }
                            val statuses = timesheetsByShiftId.mapValues { it.value.status }

                            if (!isPrimed) {
                                primedPublishedShiftIds.clear()
                                primedPublishedShiftIds.addAll(published.keys)
                                primedTimesheetStatuses.clear()
                                primedTimesheetStatuses.putAll(statuses)
                                return@collect
                            }

                            val newShiftIds = published.keys - primedPublishedShiftIds
                            newShiftIds.forEach { id ->
                                alertRosterPublished(published.getValue(id))
                            }

                            statuses.forEach { (shiftId, status) ->
                                val prevStatus = primedTimesheetStatuses[shiftId]
                                if (prevStatus != null && prevStatus != status && (status == TimesheetStatus.Approved || status == TimesheetStatus.Rejected)) {
                                    alertTimesheetDecision(shiftId, status)
                                }
                            }

                            primedPublishedShiftIds.clear()
                            primedPublishedShiftIds.addAll(published.keys)
                            primedTimesheetStatuses.clear()
                            primedTimesheetStatuses.putAll(statuses)
                        }
                } finally {
                    primingJob.cancel()
                }
            }
        }
    }

    private fun alertRosterPublished(shift: Shift) {
        post(
            notificationId = FcmEventRouting.notificationId("roster-published", shift.id),
            event = "roster-published",
            body = "A new shift was published for ${RosterFormat.dateShort(shift.date)}.",
        )
    }

    private fun alertTimesheetDecision(shiftId: String, status: TimesheetStatus) {
        val event = if (status == TimesheetStatus.Approved) "timesheet-approved" else "timesheet-rejected"
        val body = if (status == TimesheetStatus.Approved) {
            "Your submitted hours were approved."
        } else {
            "Your submitted hours were rejected — tap to review."
        }
        post(
            notificationId = FcmEventRouting.notificationId(event, shiftId),
            event = event,
            body = body,
            shiftId = shiftId,
        )
    }

    private fun post(notificationId: Int, event: String, body: String, shiftId: String? = null) {
        NotificationPoster.post(
            context = context,
            notificationId = notificationId,
            title = FcmEventRouting.defaultTitle(event),
            body = body,
            channelId = FcmEventRouting.channelId(event),
            deepLink = FcmEventRouting.deepLink(event, shiftId),
        )
    }
}
