package com.surainvestments.roster.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * The app's notification channels — Android's typed-category system, roughly mapping to the iOS
 * local-reminder slot taxonomy (`ANDROID-STAFF-BUILD-PLAN.md` §5). Each is user-mutable from
 * system settings, which the Account → Notifications section deep-links to. Created once at app
 * start (and defensively re-ensured before posting), which is safe to repeat — the platform
 * treats re-registering an existing channel id as a no-op except for name/description updates.
 */
object NotificationChannels {

    /** Pre-shift reminders + "you haven't started your shift". */
    const val SHIFT_UPCOMING = "shift-upcoming"

    /** Clock-out and submit-hours nudges after a shift ends. */
    const val TIMESHEET_ACTION = "timesheet-action"

    /** Task-related alerts (photo proof due, redo requested). */
    const val TASKS = "tasks"

    /** Everything else (messages, general announcements). */
    const val GENERAL = "general"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannels(
            listOf(
                channel(SHIFT_UPCOMING, "Shift reminders", "Upcoming shift alerts and start reminders", NotificationManager.IMPORTANCE_HIGH),
                channel(TIMESHEET_ACTION, "Timesheet & hours", "Clock-out and submit-your-hours reminders", NotificationManager.IMPORTANCE_HIGH),
                channel(TASKS, "Tasks", "Task assignments and redo requests", NotificationManager.IMPORTANCE_DEFAULT),
                channel(GENERAL, "General", "Messages and general updates", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    private fun channel(id: String, name: String, description: String, importance: Int) =
        NotificationChannel(id, name, importance).apply { this.description = description }
}
