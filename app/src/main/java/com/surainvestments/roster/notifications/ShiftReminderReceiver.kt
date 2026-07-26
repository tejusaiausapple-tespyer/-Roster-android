package com.surainvestments.roster.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by [AlarmManager][android.app.AlarmManager] when a scheduled reminder comes due — builds
 * and posts the heads-up notification, whose tap opens `MainActivity` carrying the reminder's
 * deep link. Registered non-exported: only this app's own alarm PendingIntents (component-targeted)
 * ever trigger it.
 */
class ShiftReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderArmer.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(ReminderArmer.EXTRA_BODY).orEmpty()
        val channelId = intent.getStringExtra(ReminderArmer.EXTRA_CHANNEL) ?: NotificationChannels.GENERAL
        val deepLink = intent.getStringExtra(ReminderArmer.EXTRA_DEEP_LINK)
        val notificationId = intent.getIntExtra(ReminderArmer.EXTRA_NOTIFICATION_ID, title.hashCode())

        NotificationPoster.post(context, notificationId, title, body, channelId, deepLink)
    }
}
