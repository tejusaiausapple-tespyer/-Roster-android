package com.surainvestments.roster.ui.staff.roster

import androidx.lifecycle.ViewModel
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.ClockSessionRepository
import com.surainvestments.roster.data.repository.TimesheetRepository
import com.surainvestments.roster.domain.model.ClockSession
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.domain.model.TimesheetStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Backs [SubmitHoursSheet] — a thin wrapper over the repository writes, no shift-specific state. */
@HiltViewModel
class SubmitHoursViewModel @Inject constructor(
    private val timesheetRepository: TimesheetRepository,
    private val authRepository: AuthRepository,
    private val clockSessionRepository: ClockSessionRepository,
) : ViewModel() {

    /**
     * The device-local clock session for [shiftId], if one is active and belongs to the signed-in
     * staff. Always goes through [ClockSessionRepository] (never the raw store directly) so the
     * in-memory state [com.surainvestments.roster.ui.staff.home.ClockInCard] observes stays in
     * sync with what gets cleared here — reading/writing the store directly would leave that
     * reactive state stale until the next process restart.
     */
    fun clockSessionFor(shiftId: String): ClockSession? {
        val uid = authRepository.currentUid() ?: return null
        val session = clockSessionRepository.session(uid).value ?: return null
        return session.takeIf { it.shiftId == shiftId }
    }

    /** Editable-until-approved: pending/draft edits and rejected resubmissions go through the update path. */
    private fun isEditingExisting(existing: Timesheet?): Boolean =
        existing != null &&
            (existing.status == TimesheetStatus.Rejected || existing.status == TimesheetStatus.Pending || existing.status == TimesheetStatus.Draft)

    suspend fun submit(
        shift: Shift,
        existing: Timesheet?,
        actualStart: String,
        actualEnd: String,
        breakMinutes: Int,
        workedHours: Double,
        notes: String,
    ) {
        val uid = checkNotNull(authRepository.currentUid()) { "Not signed in." }
        if (isEditingExisting(existing)) {
            timesheetRepository.resubmitTimesheet(
                id = existing!!.id,
                actualStart = actualStart,
                actualEnd = actualEnd,
                breakMinutes = breakMinutes,
                workedHours = workedHours,
                notes = notes,
            )
        } else {
            timesheetRepository.submitTimesheet(
                shiftId = shift.id,
                staffId = uid,
                actualStart = actualStart,
                actualEnd = actualEnd,
                breakMinutes = breakMinutes,
                workedHours = workedHours,
                notes = notes,
            )
        }
        // The recorded session's data now lives on the timesheet.
        if (clockSessionFor(shift.id) != null) clockSessionRepository.clear(uid)
    }
}
