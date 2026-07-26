package com.surainvestments.roster.data.local

import android.content.Context
import androidx.core.content.edit
import com.surainvestments.roster.domain.model.ScheduledReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Persists the currently-armed [ScheduledReminder] set. Two consumers:
 *  - the scheduler, to know which alarms to cancel before arming a fresh set (idempotent rebuild);
 *  - the boot receiver, to re-arm surviving alarms after a reboot clears them (AlarmManager alarms
 *    survive app-kill and Doze but not a device restart).
 *
 * Not per-uid: only the signed-in staff member's reminders are ever written, and they're fully
 * cleared/replaced on every rebuild (including to an empty set on sign-out).
 */
@Singleton
class ReminderSnapshotStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("shift_reminders", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<ScheduledReminder> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ScheduledReminder>>(raw) }.getOrDefault(emptyList())
    }

    fun save(reminders: List<ScheduledReminder>) {
        prefs.edit { putString(KEY, json.encodeToString(reminders)) }
    }

    private companion object {
        const val KEY = "armed"
    }
}
