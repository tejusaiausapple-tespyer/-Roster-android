package com.surainvestments.roster

import android.app.Application
import com.surainvestments.roster.notifications.DailyJobReminderScheduler
import com.surainvestments.roster.notifications.LocalAlertObserver
import com.surainvestments.roster.notifications.NotificationChannels
import com.surainvestments.roster.notifications.PushTokenRegistrar
import com.surainvestments.roster.notifications.ReminderResyncWorker
import com.surainvestments.roster.notifications.ShiftReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RosterApplication : Application() {

    @Inject lateinit var shiftReminderScheduler: ShiftReminderScheduler
    @Inject lateinit var dailyJobReminderScheduler: DailyJobReminderScheduler
    @Inject lateinit var pushTokenRegistrar: PushTokenRegistrar
    @Inject lateinit var localAlertObserver: LocalAlertObserver

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureChannels(this)
        // Begin keeping local shift reminders in sync with the signed-in staff member's roster;
        // observes for the whole process lifetime, no-ops while signed out.
        shiftReminderScheduler.start()
        // Same, for "check your daily jobs" reminders — independent armed set, own snapshot store.
        dailyJobReminderScheduler.start()
        // Doze/OEM-battery-manager safety net — re-arms from the last snapshot every ~6h in case
        // an alarm got silently dropped outside the scheduler's control.
        ReminderResyncWorker.schedule(this)
        // Registers/refreshes the FCM push token on every login.
        pushTokenRegistrar.start()
        // Local-alert backup for timesheet decisions + newly-published shifts, independent of push delivery.
        localAlertObserver.start()
    }
}
