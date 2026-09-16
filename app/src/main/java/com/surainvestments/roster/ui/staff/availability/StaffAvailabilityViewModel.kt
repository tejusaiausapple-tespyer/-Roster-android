package com.surainvestments.roster.ui.staff.availability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.AvailabilityLocksRepository
import com.surainvestments.roster.data.repository.AvailabilityRepository
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.DayAvailability
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.friendlyMessage
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.UserAvailability
import com.surainvestments.roster.domain.model.Weekday
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AvailabilityUiState(
    val isLoading: Boolean = true,
    val weekKey: String = RosterCalendar.weekStartKey(),
    val weekRangeLabel: String = "",
    val relativeLabel: String = "This week",
    val canGoPrev: Boolean = true,
    val canGoNext: Boolean = true,
    val isManagerLocked: Boolean = false,
    val isLocked: Boolean = false,
    val isCustomWeek: Boolean = false,
    val days: Map<Weekday, DayAvailability> = Weekday.entries.associateWith { DayAvailability.defaultDay },
    val isDirty: Boolean = false,
    val saveAsDefault: Boolean = false,
    /** Carried through so [StaffAvailabilityViewModel.save] can merge into the full map the Worker expects (a full-field overwrite, not a per-week patch). */
    val existingWeeklyAvailability: Map<String, UserAvailability> = emptyMap(),
)

/**
 * Staff-facing weekly availability — a −2…+12 week window, editable only for unlocked weeks
 * (current/past always locked; future locked only if a manager published+locked that roster
 * week). Mirrors iOS `AvailabilityView`. Saving always goes through [AvailabilityRepository]
 * (the Worker), never a direct Firestore write — see that class's doc for why.
 *
 * Simplification vs iOS: switching weeks silently discards an unsaved edit rather than
 * confirming first, and there is no "reset this week / reset all following weeks" menu yet.
 */
@HiltViewModel
class StaffAvailabilityViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val availabilityRepository: AvailabilityRepository,
    availabilityLocksRepository: AvailabilityLocksRepository,
) : ViewModel() {

    private val weekOffsetFlow = MutableStateFlow(0)
    private val editedDaysFlow = MutableStateFlow<Map<Weekday, DayAvailability>?>(null)
    private val saveAsDefaultFlow = MutableStateFlow(false)
    private val savingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    val isSaving: StateFlow<Boolean> = savingFlow.asStateFlow()
    val errorMessage: StateFlow<String?> = errorFlow.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AvailabilityUiState> = authRepository.authStateFlow()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(AvailabilityUiState(isLoading = false))
            } else {
                combine(
                    authRepository.userProfileFlow(uid),
                    weekOffsetFlow,
                    availabilityLocksRepository.lockedWeeks,
                    editedDaysFlow,
                    saveAsDefaultFlow,
                ) { user, weekOffset, lockedWeeks, edited, saveAsDefault ->
                    build(user?.weeklyAvailability ?: emptyMap(), user?.availability, weekOffset, lockedWeeks, edited, saveAsDefault)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AvailabilityUiState())

    fun onPrevWeek() {
        weekOffsetFlow.value = (weekOffsetFlow.value - 1)
            .coerceIn(BusinessRules.availabilityMinWeekOffset, BusinessRules.availabilityMaxWeekOffset)
        resetEditBuffer()
    }

    fun onNextWeek() {
        weekOffsetFlow.value = (weekOffsetFlow.value + 1)
            .coerceIn(BusinessRules.availabilityMinWeekOffset, BusinessRules.availabilityMaxWeekOffset)
        resetEditBuffer()
    }

    fun onToday() {
        weekOffsetFlow.value = 0
        resetEditBuffer()
    }

    fun onDayUpdated(weekday: Weekday, value: DayAvailability) {
        editedDaysFlow.value = uiState.value.days + (weekday to value)
    }

    fun onToggleSaveAsDefault(value: Boolean) {
        saveAsDefaultFlow.value = value
    }

    fun save() {
        val state = uiState.value
        val uid = authRepository.currentUid() ?: return
        viewModelScope.launch {
            savingFlow.value = true
            errorFlow.value = null
            try {
                val newWeek = UserAvailability(state.days)
                val targetWeeks = if (state.saveAsDefault) {
                    BusinessRules.recurringWeekKeys(state.weekKey)
                } else {
                    listOf(state.weekKey)
                }
                val updatedWeekly = state.existingWeeklyAvailability.toMutableMap()
                targetWeeks.forEach { updatedWeekly[it] = newWeek }
                availabilityRepository.save(uid, updatedWeekly)
                editedDaysFlow.value = null
                saveAsDefaultFlow.value = false
            } catch (e: Exception) {
                errorFlow.value = friendlyMessage(e, "Availability could not be saved. Please try again.")
            } finally {
                savingFlow.value = false
            }
        }
    }

    private fun resetEditBuffer() {
        editedDaysFlow.value = null
        saveAsDefaultFlow.value = false
        errorFlow.value = null
    }

    private fun build(
        weeklyAvailability: Map<String, UserAvailability>,
        legacyAvailability: UserAvailability?,
        weekOffset: Int,
        lockedWeeks: Set<String>,
        edited: Map<Weekday, DayAvailability>?,
        saveAsDefault: Boolean,
    ): AvailabilityUiState {
        val now = Instant.now()
        val clampedOffset = weekOffset.coerceIn(BusinessRules.availabilityMinWeekOffset, BusinessRules.availabilityMaxWeekOffset)
        val weekKey = RosterCalendar.addWeeksToKey(clampedOffset, RosterCalendar.weekStartKey(now))
        val loadedWeek = weeklyAvailability[weekKey] ?: legacyAvailability ?: UserAvailability.default

        return AvailabilityUiState(
            isLoading = false,
            weekKey = weekKey,
            weekRangeLabel = RosterFormat.weekRange(weekKey),
            relativeLabel = relativeLabel(clampedOffset),
            canGoPrev = clampedOffset > BusinessRules.availabilityMinWeekOffset,
            canGoNext = clampedOffset < BusinessRules.availabilityMaxWeekOffset,
            isManagerLocked = lockedWeeks.contains(weekKey) && !BusinessRules.isWeekLockedForStaff(weekKey, now),
            isLocked = BusinessRules.isWeekLockedForStaff(weekKey, lockedWeeks, now),
            isCustomWeek = weeklyAvailability.containsKey(weekKey),
            days = edited ?: loadedWeek.days,
            isDirty = edited != null && edited != loadedWeek.days,
            saveAsDefault = saveAsDefault,
            existingWeeklyAvailability = weeklyAvailability,
        )
    }

    private fun relativeLabel(offset: Int): String = when {
        offset == 0 -> "This week"
        offset == 1 -> "Next week"
        offset == -1 -> "Last week"
        offset > 1 -> "In $offset weeks"
        else -> "${-offset} weeks ago"
    }
}
