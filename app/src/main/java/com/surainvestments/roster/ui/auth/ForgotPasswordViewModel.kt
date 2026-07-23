package com.surainvestments.roster.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.domain.model.AuthError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val isWorking: Boolean = false,
    val sent: Boolean = false,
    val errorMessage: String? = null,
)

/** Android analogue of iOS's `ForgotPasswordSheet` — self-contained, not part of [AuthViewModel]. */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState

    fun prefill(email: String) {
        if (_uiState.value.email.isEmpty()) {
            _uiState.value = _uiState.value.copy(email = email)
        }
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun send() {
        val email = _uiState.value.email.trim()
        if (email.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter your email address")
            return
        }
        _uiState.value = _uiState.value.copy(isWorking = true, errorMessage = null)
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(email)
                _uiState.value = _uiState.value.copy(isWorking = false, sent = true)
            } catch (e: AuthError) {
                _uiState.value = _uiState.value.copy(isWorking = false, errorMessage = e.message)
            }
        }
    }
}
