package com.surainvestments.roster.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.surainvestments.roster.data.local.ReminderSnapshotStore

/**
 * Re-arms surviving shift reminders after a device reboot or an app update — `AlarmManager` alarms
 * survive app-kill and Doze but are cleared by a restart. Re-arms purely from the persisted
 * snapshot (no Firestore/network), matching iOS's "pre-scheduled at sync time, no background
 * fetch" model; alarms whose instant has already passed are dropped.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        NotificationChannels.ensureChannels(context)
        val armer = ReminderArmer(context)
        val now = System.currentTimeMillis()
        ReminderSnapshotStore(context).load()
            .filter { it.fireAtEpochMs > now }
            .forEach(armer::arm)
    }
}
