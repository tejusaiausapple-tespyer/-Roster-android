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
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.domain.model.TimesheetStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Fires a **local** notification immediately on two live events, without waiting for push
 * delivery to confirm — the same "local-alert backup" iOS keeps alongside FCM
 * (`ANDROID-STAFF-BUILD-PLAN.md` §5): a timesheet decision (approved/rejected), and a newly
 * published shift. **Primed on first snapshot**: the first emission after (re)login reflects
 * already-existing state and must not be diffed against nothing — that would announce every
 * shift/timesheet already on the books as "new" the moment someone signs in. Only actual
 * transitions on later emissions fire an alert.
 *
 * Deliberately independent of [RosterMessagingService] — if push is delayed or dropped, this
 * still catches the same two events from the live Firestore listeners the rest of the app
 * already holds open.
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
            var primedPublishedShiftIds: Set<String>? = null
            var primedTimesheetStatuses: Map<String, TimesheetStatus>? = null

            authRepository.authStateFlow()
                .flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf(null)
                    } else {
                        combine(shiftRepository.staffShiftsWindow(uid), timesheetRepository.staffTimesheetsByShiftId(uid)) { shifts, timesheets -> shifts to timesheets }
                    }
                }
                .collect { snapshot ->
                    if (snapshot == null) {
                        // Signed out — drop priming so the next login re-primes from a clean slate.
                        primedPublishedShiftIds = null
                        primedTimesheetStatuses = null
                        return@collect
                    }

                    val (shifts, timesheetsByShiftId) = snapshot
                    val published = shifts.filter { it.status == ShiftStatus.Published }.associateBy { it.id }
                    val statuses = timesheetsByShiftId.mapValues { it.value.status }

                    val previousShiftIds = primedPublishedShiftIds
                    val previousStatuses = primedTimesheetStatuses
                    if (previousShiftIds != null && previousStatuses != null) {
                        (published.keys - previousShiftIds).forEach { id -> alertRosterPublished(published.getValue(id)) }
                        statuses.forEach { (shiftId, status) ->
                            if (previousStatuses[shiftId] != status && (status == TimesheetStatus.Approved || status == TimesheetStatus.Rejected)) {
                                alertTimesheetDecision(shiftId, status)
                            }
                        }
                    }

                    primedPublishedShiftIds = published.keys
                    primedTimesheetStatuses = statuses
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
        post(notificationId = FcmEventRouting.notificationId(event, shiftId), event = event, body = body, shiftId = shiftId)
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
