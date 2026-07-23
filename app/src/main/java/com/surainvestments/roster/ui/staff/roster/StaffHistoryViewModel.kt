package com.surainvestments.roster.ui.staff.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.ShiftRepository
import com.surainvestments.roster.data.repository.TimesheetRepository
import com.surainvestments.roster.domain.model.HoursMetrics
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.domain.model.TimesheetStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class HistoryPeriod(val label: String) {
    Week("This week"), Month("This month"), Year("This year"), All("All time"),
}

enum class HistoryStatusFilter(val label: String, val status: TimesheetStatus?) {
    All("All statuses", null),
    Approved("Approved", TimesheetStatus.Approved),
    Pending("Pending", TimesheetStatus.Pending),
    Rejected("Rejected", TimesheetStatus.Rejected),
    AbsentReported("Absence reported", TimesheetStatus.AbsentReported),
    Absent("Absent", TimesheetStatus.Absent),
}

data class HistoryEntryUi(val timesheet: Timesheet, val shift: Shift?, val dateKey: String)

data class HistoryMonthGroupUi(val monthLabel: String, val entries: List<HistoryEntryUi>)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val metrics: HoursMetrics = HoursMetrics(),
    val groups: List<HistoryMonthGroupUi> = emptyList(),
    val isEmpty: Boolean = false,
    val period: HistoryPeriod = HistoryPeriod.Month,
    val statusFilter: HistoryStatusFilter = HistoryStatusFilter.All,
    val search: String = "",
)

/**
 * Staff-facing submitted-hours history — the full 5-year [TimesheetRepository.timesheetsForStaff]
 * list, filterable by period/status/search and grouped by month. Mirrors iOS `HistoryView`.
 * Entries whose shift has aged out of the ±28/56-day window fall back to `submittedAt` for
 * date-bucketing, same as [HoursMetrics.compute].
 */
@HiltViewModel
class StaffHistoryViewModel @Inject constructor(
    authRepository: AuthRepository,
    shiftRepository: ShiftRepository,
    timesheetRepository: TimesheetRepository,
) : ViewModel() {

    private val periodFlow = MutableStateFlow(HistoryPeriod.Month)
    private val statusFilterFlow = MutableStateFlow(HistoryStatusFilter.All)
    private val searchFlow = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = authRepository.authStateFlow()
        .flatMapLatest { staffId ->
            if (staffId == null) {
                flowOf(HistoryUiState(isLoading = false))
            } else {
                combine(
                    shiftRepository.staffShiftsById(staffId),
                    timesheetRepository.staffTimesheetsByShiftId(staffId),
                    periodFlow,
                    statusFilterFlow,
                    searchFlow,
                ) { shiftsById, timesheetsByShiftId, period, statusFilter, search ->
                    build(shiftsById.values.toList(), timesheetsByShiftId.values.toList(), period, statusFilter, search)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setPeriod(period: HistoryPeriod) {
        periodFlow.value = period
    }

    fun setStatusFilter(filter: HistoryStatusFilter) {
        statusFilterFlow.value = filter
    }

    fun setSearch(query: String) {
        searchFlow.value = query
    }

    private fun build(
        shifts: List<Shift>,
        timesheets: List<Timesheet>,
        period: HistoryPeriod,
        statusFilter: HistoryStatusFilter,
        search: String,
    ): HistoryUiState {
        val now = Instant.now()
        val shiftsById = shifts.associateBy { it.id }

        val entries = timesheets
            .map { ts ->
                val shift = shiftsById[ts.shiftId]
                val dateKey = shift?.date ?: ts.submittedAt?.let { RosterCalendar.todayKey(it) } ?: ""
                HistoryEntryUi(ts, shift, dateKey)
            }
            .filter { inPeriod(it.dateKey, period, now) }
            .filter { statusFilter.status == null || it.timesheet.status == statusFilter.status }
            .filter { matchesSearch(it, search) }
            .sortedByDescending { it.dateKey }

        val groups = entries
            .groupBy { it.dateKey.take(7) } // yyyy-MM
            .toSortedMap(compareByDescending { it })
            .map { (monthKey, list) -> HistoryMonthGroupUi(monthLabel(monthKey), list) }

        return HistoryUiState(
            isLoading = false,
            metrics = HoursMetrics.compute(timesheets, shifts, now),
            groups = groups,
            isEmpty = entries.isEmpty(),
            period = period,
            statusFilter = statusFilter,
            search = search,
        )
    }

    private fun inPeriod(dateKey: String, period: HistoryPeriod, now: Instant): Boolean {
        if (period == HistoryPeriod.All) return true
        val date = RosterCalendar.parseDateKey(dateKey) ?: return false
        val nowZoned = ZonedDateTime.ofInstant(now, RosterCalendar.zoneId)
        return when (period) {
            HistoryPeriod.Week -> RosterCalendar.weekStartKey(date.atStartOfDay(RosterCalendar.zoneId).toInstant()) == RosterCalendar.weekStartKey(now)
            HistoryPeriod.Month -> YearMonth.from(date) == YearMonth.from(nowZoned)
            HistoryPeriod.Year -> date.year == nowZoned.year
            HistoryPeriod.All -> true
        }
    }

    private fun matchesSearch(entry: HistoryEntryUi, query: String): Boolean {
        val q = query.trim().lowercase(Locale.ENGLISH)
        if (q.isEmpty()) return true
        val location = entry.shift?.location?.lowercase(Locale.ENGLISH) ?: ""
        val dateText = entry.dateKey.lowercase(Locale.ENGLISH)
        return location.contains(q) || dateText.contains(q)
    }

    private fun monthLabel(yyyyMm: String): String {
        val ym = runCatching { YearMonth.parse(yyyyMm) }.getOrNull() ?: return yyyyMm
        return DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH).format(ym)
    }
}
