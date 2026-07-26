package com.surainvestments.roster.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.surainvestments.roster.domain.model.ScheduledReminder

/**
 * Arms/cancels a single [ScheduledReminder] with [AlarmManager], keyed to the reminder's absolute
 * fire instant (`RTC_WAKEUP`) so a device timezone change can't shift it. Uses
 * `setExactAndAllowWhileIdle` (fires precisely even in Doze, survives app-kill); on API 31+ where
 * exact-alarm scheduling isn't permitted it degrades to the inexact allow-while-idle variant
 * rather than failing. Shared by the live scheduler and the boot re-arm receiver.
 */
class ReminderArmer(private val context: Context) {

    private val alarmManager = context.getSystemService<AlarmManager>()

    fun arm(reminder: ScheduledReminder) {
        val manager = alarmManager ?: return
        val intent = reminderIntent(context).apply {
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_BODY, reminder.body)
            putExtra(EXTRA_CHANNEL, reminder.channelId)
            putExtra(EXTRA_DEEP_LINK, reminder.deepLink)
            putExtra(EXTRA_NOTIFICATION_ID, reminder.requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireAtEpochMs, pendingIntent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireAtEpochMs, pendingIntent)
        }
    }

    fun cancel(requestCode: Int) {
        val manager = alarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            reminderIntent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        manager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    companion object {
        const val ACTION_SHIFT_REMINDER = "com.surainvestments.roster.action.SHIFT_REMINDER"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_DEEP_LINK = "deepLink"
        const val EXTRA_NOTIFICATION_ID = "notificationId"

        /** Explicit (same-app, component-targeted) intent so the matching cancel PendingIntent resolves identically. */
        private fun reminderIntent(context: Context) =
            Intent(context, ShiftReminderReceiver::class.java).setAction(ACTION_SHIFT_REMINDER)
    }
}
