package com.surainvestments.roster.ui.staff.roster

import androidx.lifecycle.ViewModel
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.TimesheetRepository
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.Timesheet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Backs [ReportAbsenceSheet] — a thin wrapper over the repository write. */
@HiltViewModel
class ReportAbsenceViewModel @Inject constructor(
    private val timesheetRepository: TimesheetRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    suspend fun reportAbsence(shift: Shift, existing: Timesheet?, reason: String) {
        val uid = checkNotNull(authRepository.currentUid()) { "Not signed in." }
        timesheetRepository.reportAbsence(shiftId = shift.id, staffId = uid, existing = existing, reason = reason)
    }
}
