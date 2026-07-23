package com.surainvestments.roster.data.service

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.Shift
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of [CalendarService.addShift]. Mirrors iOS `CalendarService.Result`. */
sealed interface CalendarResult {
    data object Added : CalendarResult
    data class SharedFile(val uri: Uri) : CalendarResult
    data class Failed(val message: String) : CalendarResult
}

/**
 * Adds a shift to the staff member's device calendar via [CalendarContract], with a 1-hour-before
 * reminder; falls back to sharing a generated `.ics` file if calendar write access is denied.
 * Mirrors iOS `CalendarService` (EventKit there, `CalendarContract` here — same fallback shape).
 */
@Singleton
class CalendarService @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun addShift(shift: Shift, companyName: String): CalendarResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            val uri = writeIcs(shift, companyName)
            return@withContext uri?.let { CalendarResult.SharedFile(it) } ?: CalendarResult.Failed("Calendar access was denied.")
        }
        try {
            val calendarId = defaultCalendarId() ?: return@withContext CalendarResult.Failed("No calendar available on this device.")
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, shiftTitle(shift, companyName))
                put(CalendarContract.Events.DTSTART, shift.startDateTime.toEpochMilli())
                put(CalendarContract.Events.DTEND, shift.endDateTime.toEpochMilli())
                put(CalendarContract.Events.EVENT_LOCATION, shift.location.orEmpty())
                put(CalendarContract.Events.DESCRIPTION, shift.notes.orEmpty())
                put(CalendarContract.Events.EVENT_TIMEZONE, RosterCalendar.zoneId.id)
            }
            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return@withContext CalendarResult.Failed("Could not save the event.")
            val eventId = ContentUris.parseId(eventUri)
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, 60)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)
            CalendarResult.Added
        } catch (e: Exception) {
            CalendarResult.Failed("Could not save the event.")
        }
    }

    /** The device's primary writable calendar, or the first writable one found. */
    private fun defaultCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            var fallback: Long? = null
            while (cursor.moveToNext()) {
                val accessLevel = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL))
                if (accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                val isPrimary = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)) != 0
                if (isPrimary) return id
                if (fallback == null) fallback = id
            }
            return fallback
        }
        return null
    }

    private fun shiftTitle(shift: Shift, companyName: String): String =
        shift.location?.takeIf { it.isNotBlank() }?.let { "$companyName — $it" } ?: "$companyName Shift"

    // MARK: ICS fallback

    private val icsStampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    private fun writeIcs(shift: Shift, companyName: String): Uri? {
        val ics = buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("PRODID:-//Rosterra//Staff//EN\r\n")
            append("BEGIN:VEVENT\r\n")
            append("UID:${shift.id}@sura-roster\r\n")
            append("DTSTAMP:${icsStampFormatter.format(Instant.now())}\r\n")
            append("DTSTART:${icsStampFormatter.format(shift.startDateTime)}\r\n")
            append("DTEND:${icsStampFormatter.format(shift.endDateTime)}\r\n")
            append("SUMMARY:${shiftTitle(shift, companyName)}\r\n")
            append("LOCATION:${shift.location.orEmpty()}\r\n")
            append("BEGIN:VALARM\r\n")
            append("TRIGGER:-PT1H\r\n")
            append("ACTION:DISPLAY\r\n")
            append("DESCRIPTION:Upcoming shift\r\n")
            append("END:VALARM\r\n")
            append("END:VEVENT\r\n")
            append("END:VCALENDAR\r\n")
        }
        return try {
            val dir = File(context.cacheDir, "calendar_shares").apply { mkdirs() }
            val file = File(dir, "shift-${shift.id}.ics")
            file.writeText(ics)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}
