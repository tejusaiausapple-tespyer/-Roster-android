package com.surainvestments.roster.data.local

import android.content.Context
import androidx.core.content.edit
import com.surainvestments.roster.domain.model.ScheduledReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Persists the currently-armed daily-jobs [ScheduledReminder] set. Same role as
 * [ReminderSnapshotStore] (idempotent rebuild + boot re-arm), kept as a separate prefs file/key
 * so the two independent schedulers never clobber each other's armed set.
 */
@Singleton
class DailyJobReminderSnapshotStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("daily_job_reminders", Context.MODE_PRIVATE)
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
