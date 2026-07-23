package com.surainvestments.roster.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AccountDeletionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountDeletionUiState(
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

/** Staff self-service side of the ATO-safe deletion flow — Android analogue of iOS's `AccountView` delete-account section. */
@HiltViewModel
class AccountDeletionViewModel @Inject constructor(
    private val repository: AccountDeletionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountDeletionUiState())
    val uiState: StateFlow<AccountDeletionUiState> = _uiState

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    fun requestDeletion() {
        _uiState.value = _uiState.value.copy(isWorking = true, errorMessage = null)
        viewModelScope.launch {
            try {
                repository.requestOwnAccountDeletion()
                _uiState.value = _uiState.value.copy(isWorking = false, successMessage = "Deletion request sent to your manager.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isWorking = false, errorMessage = e.message ?: "Couldn't send request")
            }
        }
    }
}
