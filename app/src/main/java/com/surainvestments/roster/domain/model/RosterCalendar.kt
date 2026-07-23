package com.surainvestments.roster.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Mirrors iOS `RosterCalendar` (`Models/RosterCalendar.swift`) — the business always
 * operates on Adelaide-local calendar days regardless of device timezone, and weeks
 * start Monday. `java.time.DayOfWeek.value` is already 1=Monday..7=Sunday, so unlike
 * iOS's `Calendar` (Sunday-first) no weekday remap is needed here.
 */
object RosterCalendar {
    val zoneId: ZoneId = ZoneId.of("Australia/Adelaide")

    private val dayKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    /** yyyy-MM-dd in the Adelaide calendar day, e.g. "2026-07-22". */
    fun todayKey(now: Instant = Instant.now()): String =
        dayKeyFormatter.format(ZonedDateTime.ofInstant(now, zoneId))

    /** 1=Monday..7=Sunday, in the Adelaide calendar day. */
    fun weekday(now: Instant = Instant.now()): Int =
        ZonedDateTime.ofInstant(now, zoneId).dayOfWeek.value

    /** 1=Monday..7=Sunday for an arbitrary yyyy-MM-dd date key (not just "now"). */
    fun weekdayForKey(dateKey: String): Int = parseDateKey(dateKey)?.dayOfWeek?.value ?: 1

    /** yyyy-MM-dd [offsetDays] from today in the Adelaide calendar day (negative for the past). */
    fun dateKey(offsetDays: Long, now: Instant = Instant.now()): String =
        dayKeyFormatter.format(ZonedDateTime.ofInstant(now, zoneId).plusDays(offsetDays))

    fun parseDateKey(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    /** yyyy-MM-dd of the Monday starting the week containing [now], in the Adelaide calendar day. */
    fun weekStartKey(now: Instant = Instant.now()): String {
        val date = ZonedDateTime.ofInstant(now, zoneId).toLocalDate()
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return dayKeyFormatter.format(monday)
    }

    /** yyyy-MM for the current Adelaide calendar month, e.g. "2026-07". */
    fun monthKey(now: Instant = Instant.now()): String =
        YearMonth.from(ZonedDateTime.ofInstant(now, zoneId)).toString()

    /** The 7 yyyy-MM-dd keys (Mon..Sun) of the week starting at [mondayKey]. */
    fun weekDayKeys(mondayKey: String): List<String> {
        val monday = parseDateKey(mondayKey) ?: return emptyList()
        return (0..6).map { dayKeyFormatter.format(monday.plusDays(it.toLong())) }
    }

    /** [mondayKey] shifted by [weeks] whole weeks, e.g. -1 for the previous week's Monday. */
    fun addWeeksToKey(weeks: Int, mondayKey: String): String {
        val monday = parseDateKey(mondayKey) ?: return mondayKey
        return dayKeyFormatter.format(monday.plusWeeks(weeks.toLong()))
    }

    /**
     * Inclusive/exclusive `[start, end)` yyyy-MM-dd bounds for [monthKey] — used as a
     * document-id range (doc id = "{periodStart}_{staffId}") to scope a payslip query to one
     * month without a composite index, mirroring iOS's `monthDayKeyBounds`. Returns null for
     * an unparseable month key.
     */
    fun monthDayKeyBounds(monthKey: String): Pair<String, String>? {
        val month = runCatching { YearMonth.parse(monthKey) }.getOrNull() ?: return null
        val start = "$monthKey-01"
        val next = month.plusMonths(1)
        val end = "%04d-%02d-01".format(next.year, next.monthValue)
        return start to end
    }
}

/** Mirrors iOS `RosterFormat` (`Models/RosterFormat.swift`) — Adelaide-zoned display strings. */
object RosterFormat {
    private val fullDateFormatter = DateTimeFormatter
        .ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
        .withZone(RosterCalendar.zoneId)

    private val timeFormatter = DateTimeFormatter
        .ofPattern("h:mm a", Locale.ENGLISH)
        .withZone(RosterCalendar.zoneId)

    private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
    private val weekdayShortFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val dayOfMonthFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** e.g. "Wednesday, 22 July 2026". */
    fun dateFull(instant: Instant): String = fullDateFormatter.format(instant)

    /** 12-hour clock, e.g. "3:45 PM". */
    fun time(instant: Instant): String = timeFormatter.format(instant)

    /** e.g. "Wednesday, 22 Jul" for a yyyy-MM-dd date key — used to group shift-list section headers. */
    fun dayHeader(dateKey: String): String =
        RosterCalendar.parseDateKey(dateKey)?.let { dayHeaderFormatter.format(it) } ?: dateKey

    /** e.g. "Wed" for a yyyy-MM-dd date key. */
    fun weekdayShort(dateKey: String): String =
        RosterCalendar.parseDateKey(dateKey)?.let { weekdayShortFormatter.format(it) } ?: dateKey

    /** e.g. "22 Jul" for a yyyy-MM-dd date key. */
    fun dayOfMonth(dateKey: String): String =
        RosterCalendar.parseDateKey(dateKey)?.let { dayOfMonthFormatter.format(it) } ?: dateKey

    /** 12-hour clock from an "HH:mm" string, e.g. "15:45" → "3:45 PM". */
    fun timeOfDay(hhmm: String): String {
        val time = runCatching { java.time.LocalTime.parse(hhmm) }.getOrNull() ?: return hhmm
        return java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).format(time)
    }

    /** e.g. "22 Jul 2026" for a yyyy-MM-dd date key. */
    fun dateShort(dateKey: String): String =
        RosterCalendar.parseDateKey(dateKey)?.let { shortDateFormatter.format(it) } ?: dateKey

    private val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayOnlyFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
    private val weekdayInitialFormatter = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)

    /** e.g. "1 – 7 Jul" (same month) or "29 Jun – 5 Jul" (spanning months), for a Monday date key. */
    fun weekRange(mondayKey: String): String {
        val monday = RosterCalendar.parseDateKey(mondayKey) ?: return mondayKey
        val sunday = monday.plusDays(6)
        return if (monthFormatter.format(monday) == monthFormatter.format(sunday)) {
            "${dayOnlyFormatter.format(monday)} – ${dayMonthFormatter.format(sunday)}"
        } else {
            "${dayMonthFormatter.format(monday)} – ${dayMonthFormatter.format(sunday)}"
        }
    }

    /** Single-letter weekday, e.g. "M", for a yyyy-MM-dd date key. */
    fun weekdayInitial(dateKey: String): String =
        RosterCalendar.parseDateKey(dateKey)?.let { weekdayInitialFormatter.format(it) } ?: dateKey

    /** Day-of-month number, e.g. "22", for a yyyy-MM-dd date key. */
    fun dayNumber(dateKey: String): String =
        RosterCalendar.parseDateKey(dateKey)?.dayOfMonth?.toString() ?: dateKey

    private val moneyFormatter = java.text.NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("AU").build())

    /** AU currency, e.g. "$1,234.50". */
    fun money(value: Double): String = moneyFormatter.format(value)

    /** Compact decimal hours for stat tiles, e.g. "7.5" or "8" (no trailing ".0"). */
    fun decimalHours(value: Double): String =
        if (value == Math.floor(value)) value.toInt().toString() else String.format(Locale.ENGLISH, "%.1f", value)
}
