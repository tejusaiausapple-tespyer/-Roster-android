package com.surainvestments.roster.notifications

import android.content.Context
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.data.local.ReminderSnapshotStore
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.ClockSessionRepository
import com.surainvestments.roster.data.repository.ShiftRepository
import com.surainvestments.roster.data.repository.TimesheetRepository
import com.surainvestments.roster.domain.model.ScheduledReminder
import com.surainvestments.roster.domain.model.ShiftReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the device's armed local shift reminders in sync with the signed-in staff member's live
 * roster — the Android analogue of iOS `RosterRepository` driving `ShiftReminderScheduler.sync`
 * on every shifts/timesheets change (`IOS-STAFF-AUDIT.md` §10). Started once from
 * [com.surainvestments.roster.RosterApplication]; observes for the whole process lifetime on the
 * [ApplicationScope].
 *
 * On every change to the shift window, the staff member's timesheets, or the active clock session,
 * it recomputes the full reminder set (pure [ShiftReminderPlanner]) and rebuilds the alarms
 * idempotently: cancel the previously-armed set, arm the new one, persist it for boot re-arm.
 * Signing out emits an empty set, which clears every alarm.
 */
@Singleton
class ShiftReminderScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val authRepository: AuthRepository,
    private val shiftRepository: ShiftRepository,
    private val timesheetRepository: TimesheetRepository,
    private val clockSessionRepository: ClockSessionRepository,
    private val snapshotStore: ReminderSnapshotStore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val armer = ReminderArmer(context)
    private val rebuildLock = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        appScope.launch {
            authRepository.authStateFlow()
                .flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            shiftRepository.staffShiftsWindow(uid),
                            timesheetRepository.staffTimesheetsByShiftId(uid),
                            clockSessionRepository.session(uid),
                        ) { shifts, timesheetsByShiftId, session ->
                            ShiftReminderPlanner.plan(
                                shifts = shifts,
                                timesheetsByShiftId = timesheetsByShiftId,
                                clockedInShiftId = session?.takeIf { it.isActive }?.shiftId,
                                now = Instant.now(),
                            )
                        }
                    }
                }
                .distinctUntilChanged()
                .collect { reschedule(it) }
        }
    }

    private suspend fun reschedule(reminders: List<ScheduledReminder>) {
        rebuildLock.withLock {
            snapshotStore.load().forEach { armer.cancel(it.requestCode) }
            reminders.forEach(armer::arm)
            snapshotStore.save(reminders)
        }
    }
}
