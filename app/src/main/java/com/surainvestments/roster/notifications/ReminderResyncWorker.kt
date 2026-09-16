package com.surainvestments.roster.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.surainvestments.roster.data.local.DailyJobReminderSnapshotStore
import com.surainvestments.roster.data.local.ReminderSnapshotStore
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Doze/OEM-battery-manager safety net (`ANDROID-STAFF-BUILD-PLAN.md` §5): some Samsung/Xiaomi-style
 * battery optimizers silently drop scheduled alarms outside the app's control. This periodically
 * re-arms every not-yet-fired reminder from the last persisted snapshot — no Firestore read, no
 * business-rule recomputation, just "make sure what we already decided to schedule is still
 * scheduled." The live [ShiftReminderScheduler] and [DailyJobReminderScheduler] remain the source
 * of truth for *what* to schedule; this only guards against alarms they already armed getting
 * lost silently.
 */
class ReminderResyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val armer = ReminderArmer(applicationContext)
        val now = System.currentTimeMillis()
        ReminderSnapshotStore(applicationContext).load()
            .filter { it.fireAtEpochMs > now }
            .forEach(armer::arm)
        DailyJobReminderSnapshotStore(applicationContext).load()
            .filter { it.fireAtEpochMs > now }
            .forEach(armer::arm)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "reminder-resync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderResyncWorker>(Duration.ofHours(6))
                .setInitialDelay(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
