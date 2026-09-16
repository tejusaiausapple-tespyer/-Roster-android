package com.surainvestments.roster.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.domain.model.AuthError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

data class ChangeEmailUiState(
    val currentPassword: String = "",
    val newEmail: String = "",
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val succeeded: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isWorking && currentPassword.isNotEmpty() && EMAIL_PATTERN.matches(newEmail.trim())
}

/** Android analogue of iOS's `ChangeEmailView` — Account tab sheet, mirrors [ChangePasswordViewModel]'s shape exactly. */
@HiltViewModel
class ChangeEmailViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeEmailUiState())
    val uiState: StateFlow<ChangeEmailUiState> = _uiState

    fun onCurrentPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(currentPassword = value, errorMessage = null)
    }

    fun onNewEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(newEmail = value, errorMessage = null)
    }

    fun submit() {
        val state = _uiState.value
        _uiState.value = state.copy(isWorking = true, errorMessage = null)
        viewModelScope.launch {
            try {
                authRepository.changeEmail(state.currentPassword, state.newEmail.trim())
                _uiState.value = _uiState.value.copy(isWorking = false, succeeded = true)
            } catch (e: AuthError) {
                _uiState.value = _uiState.value.copy(isWorking = false, errorMessage = e.message)
            }
        }
    }

    /**
     * Called when [ChangeEmailScreen] leaves composition. Same reasoning as
     * `ProfileEditViewModel.resetForNextOpen`: this dialog has no back-stack entry of its own, so
     * the same ViewModel instance persists across closes — without this, reopening "Change email"
     * after a successful request would show the old "check your inbox" confirmation (and the
     * now-stale password field) instead of a fresh form.
     */
    fun resetForNextOpen() {
        _uiState.value = ChangeEmailUiState()
    }
}
