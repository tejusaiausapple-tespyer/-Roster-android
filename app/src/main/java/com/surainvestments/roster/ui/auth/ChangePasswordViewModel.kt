package com.surainvestments.roster.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.domain.model.AuthError
import com.surainvestments.roster.domain.model.PasswordRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val succeeded: Boolean = false,
) {
    val rules: List<PasswordRules.Rule> get() = PasswordRules.rules(newPassword)

    val canSubmit: Boolean
        get() = !isWorking &&
            currentPassword.isNotEmpty() &&
            newPassword.isNotEmpty() &&
            newPassword == confirmPassword &&
            PasswordRules.errors(newPassword).isEmpty()
}

/** Android analogue of iOS's `ChangePasswordView` — used both as the Phase 4 forced gate and (later) an Account-tab sheet. */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState

    fun onCurrentPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(currentPassword = value, errorMessage = null)
    }

    fun onNewPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(newPassword = value, errorMessage = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null)
    }

    fun submit(isForced: Boolean) {
        val state = _uiState.value
        if (state.newPassword != state.confirmPassword) {
            _uiState.value = state.copy(errorMessage = "Passwords do not match")
            return
        }
        val ruleErrors = PasswordRules.errors(state.newPassword)
        if (ruleErrors.isNotEmpty()) {
            _uiState.value = state.copy(errorMessage = ruleErrors.first())
            return
        }

        _uiState.value = state.copy(isWorking = true, errorMessage = null)
        viewModelScope.launch {
            try {
                authRepository.changePassword(state.currentPassword, state.newPassword, wasForced = isForced)
                _uiState.value = _uiState.value.copy(isWorking = false, succeeded = true)
            } catch (e: AuthError) {
                _uiState.value = _uiState.value.copy(isWorking = false, errorMessage = e.message)
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
