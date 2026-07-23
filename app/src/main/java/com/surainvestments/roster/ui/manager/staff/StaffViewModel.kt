package com.surainvestments.roster.ui.manager.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.surainvestments.roster.data.repository.AccountDeletionRepository
import com.surainvestments.roster.data.repository.StaffRepository
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.EmploymentType
import com.surainvestments.roster.domain.model.UserStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaffActionUiState(
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

/** Android analogue of iOS's `ManagerStaffView` + `ManagerAddStaffSheet`, backed by [StaffRepository]. */
@HiltViewModel
class StaffViewModel @Inject constructor(
    private val staffRepository: StaffRepository,
    private val accountDeletionRepository: AccountDeletionRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    val staffList: StateFlow<List<AppUser>> = staffRepository.staffListFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(StaffActionUiState())
    val uiState: StateFlow<StaffActionUiState> = _uiState

    private val actorUid: String? get() = firebaseAuth.currentUser?.uid

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    fun createStaff(
        fullName: String,
        email: String,
        password: String,
        employmentType: EmploymentType,
        phone: String?,
        startDate: String?,
        onSuccess: () -> Unit,
    ) = runAction {
        val actor = actorUid ?: throw IllegalStateException("Not signed in")
        staffRepository.createStaff(fullName, email, password, employmentType, phone, startDate, actor)
        "Staff account created" to onSuccess
    }

    fun setStatus(staffId: String, status: UserStatus, onSuccess: () -> Unit) = runAction {
        val actor = actorUid ?: throw IllegalStateException("Not signed in")
        staffRepository.setStaffStatus(staffId, status, actor)
        "Updated" to onSuccess
    }

    fun resetPassword(staffId: String, temporaryPassword: String, onSuccess: () -> Unit) = runAction {
        staffRepository.resetStaffPassword(staffId, temporaryPassword)
        "Temporary password set" to onSuccess
    }

    fun changeEmail(staffId: String, newEmail: String, managerPassword: String, onSuccess: () -> Unit) = runAction {
        staffRepository.changeStaffEmail(staffId, newEmail, managerPassword)
        "Email updated" to onSuccess
    }

    fun updateStaffFields(staffId: String, fields: Map<String, Any>, onSuccess: () -> Unit) = runAction {
        val actor = actorUid ?: throw IllegalStateException("Not signed in")
        staffRepository.updateStaffFields(staffId, fields, actor)
        "Staff details saved" to onSuccess
    }

    fun requestEmailChange(staffId: String, onSuccess: () -> Unit) = runAction {
        val actor = actorUid ?: throw IllegalStateException("Not signed in")
        staffRepository.requestStaffEmailChange(staffId, actor)
        "Email change requested" to onSuccess
    }

    fun cancelEmailChangeRequest(staffId: String, onSuccess: () -> Unit) = runAction {
        val actor = actorUid ?: throw IllegalStateException("Not signed in")
        staffRepository.cancelStaffEmailChange(staffId, actor)
        "Email change request cancelled" to onSuccess
    }

    fun requireNewAddress(staffId: String, onSuccess: () -> Unit) = runAction {
        val actor = actorUid ?: throw IllegalStateException("Not signed in")
        staffRepository.requestStaffAddressUpdate(staffId, actor)
        "Staff will be asked for a new address on next login" to onSuccess
    }

    fun approveDeletion(staffId: String, onSuccess: () -> Unit) = runAction {
        accountDeletionRepository.approveStaffAccountDeletion(staffId)
        "Account locked — Auth purge in 30 days" to onSuccess
    }

    fun declineDeletion(staffId: String, onSuccess: () -> Unit) = runAction {
        accountDeletionRepository.declineStaffAccountDeletion(staffId)
        "Deletion request declined" to onSuccess
    }

    fun cancelDeletion(staffId: String, onSuccess: () -> Unit) = runAction {
        accountDeletionRepository.cancelStaffAccountDeletion(staffId)
        "Account reinstated" to onSuccess
    }

    private fun runAction(block: suspend () -> Pair<String, () -> Unit>) {
        _uiState.value = _uiState.value.copy(isWorking = true, errorMessage = null, successMessage = null)
        viewModelScope.launch {
            try {
                val (message, onSuccess) = block()
                _uiState.value = _uiState.value.copy(isWorking = false, successMessage = message)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isWorking = false, errorMessage = e.message ?: "Something went wrong")
            }
        }
    }
}
