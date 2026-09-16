package com.surainvestments.roster.notifications

import android.content.Context
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.data.local.DailyJobReminderSnapshotStore
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.DailyJobRepository
import com.surainvestments.roster.data.repository.ShiftRepository
import com.surainvestments.roster.domain.model.DailyJobReminderPlanner
import com.surainvestments.roster.domain.model.ScheduledReminder
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
 * Keeps the device's armed "check your daily jobs" reminders in sync with the signed-in staff
 * member's live roster + today's job assignments — the Android analogue of iOS
 * `RosterRepository` driving `DailyJobReminderScheduler.sync`. Started once from
 * [com.surainvestments.roster.RosterApplication]; observes for the whole process lifetime, same
 * shape as [ShiftReminderScheduler] but a fully independent armed set (own snapshot store, own
 * slot-tag namespace) so the two schedulers never interfere with each other.
 *
 * Reacts to both the shift window and daily-job assignments across that same window: completing
 * the last job for a shift removes it from [DailyJobReminderPlanner]'s output on the next
 * emission, which cancels its remaining reminders here — no separate "all done" cancellation path
 * needed, mirroring iOS. Deliberately subscribes to the whole window
 * ([DailyJobRepository.assignmentsForStaffWindow]), not a single-day query — this scheduler runs
 * for the whole process lifetime with no screen lifecycle to re-subscribe it, so a query scoped to
 * "today" at subscribe time would silently go stale the instant local midnight passes. The
 * planner re-derives "today" fresh from `now` on every emission instead.
 */
@Singleton
class DailyJobReminderScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val authRepository: AuthRepository,
    private val shiftRepository: ShiftRepository,
    private val dailyJobRepository: DailyJobRepository,
    private val snapshotStore: DailyJobReminderSnapshotStore,
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
                            dailyJobRepository.assignmentsForStaffWindow(uid),
                        ) { shifts, windowAssignments ->
                            DailyJobReminderPlanner.plan(
                                shifts = shifts,
                                windowAssignments = windowAssignments,
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
