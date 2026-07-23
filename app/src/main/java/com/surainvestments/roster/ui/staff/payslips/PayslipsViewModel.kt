package com.surainvestments.roster.ui.staff.payslips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.PayslipRepository
import com.surainvestments.roster.domain.model.Payslip
import com.surainvestments.roster.domain.model.RosterCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val monthLabelFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

data class PayslipsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val monthKey: String = RosterCalendar.monthKey(),
    val payslips: List<Payslip> = emptyList(),
    val errorMessage: String? = null,
) {
    val monthLabel: String get() = monthLabelFormatter.format(YearMonth.parse(monthKey))

    /** Payslips can't exist for a period that hasn't happened yet. */
    val canGoNext: Boolean get() = monthKey < RosterCalendar.monthKey()
}

/**
 * Staff Payslips list — cache-first, month-scoped, never a live listener (mirrors iOS
 * `PayslipsView`'s loading strategy exactly; see [PayslipRepository]). Navigating months or
 * pulling to refresh both re-fetch through the same 3-tier strategy.
 */
@HiltViewModel
class PayslipsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val payslipRepository: PayslipRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PayslipsUiState())
    val uiState: StateFlow<PayslipsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun previousMonth() {
        _uiState.update { it.copy(monthKey = YearMonth.parse(it.monthKey).minusMonths(1).toString()) }
        load()
    }

    fun nextMonth() {
        if (!_uiState.value.canGoNext) return
        _uiState.update { it.copy(monthKey = YearMonth.parse(it.monthKey).plusMonths(1).toString()) }
        load()
    }

    fun refresh() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean = false) {
        val staffId = authRepository.currentUid() ?: return
        val monthKey = _uiState.value.monthKey
        _uiState.update { it.copy(isLoading = !forceRefresh, isRefreshing = forceRefresh, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { payslipRepository.staffPayslips(staffId, monthKey, forceRefresh) }
            // The user may have already navigated to a different month by the time this resolves —
            // don't let a slow, stale response overwrite what's currently on screen.
            if (_uiState.value.monthKey != monthKey) return@launch
            result.fold(
                onSuccess = { slips -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, payslips = slips) } },
                onFailure = {
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, errorMessage = "Couldn't load payslips. Check your connection and try again.")
                    }
                },
            )
        }
    }
}
