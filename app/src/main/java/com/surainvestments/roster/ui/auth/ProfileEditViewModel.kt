package com.surainvestments.roster.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.domain.model.friendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ProfileEditUiState(
    val isLoading: Boolean = true,
    val fullName: String = "",
    val phone: String = "",
    val dob: String = "",
    val address: String = "",
    val emergencyContact: String = "",
    val wasProfileUpdateRequired: Boolean = false,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val succeeded: Boolean = false,
) {
    /**
     * Mirrors [com.surainvestments.roster.domain.model.AppUser.needsProfileCompletion]'s
     * `complete` check (dob/address/phone all non-blank) — used only when this form is shown as
     * the blocking [AppRoute.ProfileCompletion] gate, not the optional Account-tab edit.
     */
    fun canSubmit(requireCompletion: Boolean): Boolean =
        !isWorking && fullName.isNotBlank() &&
            (!requireCompletion || (phone.isNotBlank() && dob.isNotBlank() && address.isNotBlank()))
}

/**
 * Backs both the [AppRoute.ProfileCompletion] gate and the Account tab's "Edit profile" screen —
 * same fields, same write path ([AuthRepository.updateProfile]), just a different completeness
 * requirement. Loads the signed-in user's current values once (not a live listener) since this is
 * a plain edit form, not a screen that needs to react to concurrent external changes mid-edit.
 */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        val uid = authRepository.currentUid()
        if (uid == null) {
            _uiState.value = _uiState.value.copy(isLoading = false)
        } else {
            viewModelScope.launch {
                val user = authRepository.userProfileFlow(uid).first()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    fullName = user?.fullName.orEmpty(),
                    phone = user?.phone.orEmpty(),
                    dob = user?.dob.orEmpty(),
                    address = user?.address.orEmpty(),
                    emergencyContact = user?.emergencyContactName.orEmpty(),
                    wasProfileUpdateRequired = user?.profileUpdateRequired ?: false,
                )
            }
        }
    }

    /**
     * Called when [EditProfileScreen] leaves composition — this ViewModel has no back-stack entry
     * of its own (it's a plain `showEditProfile` boolean toggle inside `AccountTabContent`, not a
     * NavHost destination), so `hiltViewModel()` keeps returning the *same* instance every time
     * the sheet reopens. Without this, a stale `succeeded` from a previous save would still be
     * `true` on the very next open — confirmed live: after one successful save, every later open
     * immediately bounced straight back to Account because `LaunchedEffect(uiState.succeeded)`
     * saw `true` on the very first frame, before the form ever had a chance to render. Re-fetches
     * from Firestore fresh rather than just clearing fields, so a reopen also picks up any change
     * made elsewhere in the meantime (e.g. a manager edit) and discards an abandoned unsaved draft.
     */
    fun resetForNextOpen() {
        _uiState.value = ProfileEditUiState()
        load()
    }

    fun onFullNameChange(value: String) {
        _uiState.value = _uiState.value.copy(fullName = value, errorMessage = null)
    }

    fun onPhoneChange(value: String) {
        _uiState.value = _uiState.value.copy(phone = value, errorMessage = null)
    }

    fun onDobChange(value: String) {
        _uiState.value = _uiState.value.copy(dob = value, errorMessage = null)
    }

    fun onAddressChange(value: String) {
        _uiState.value = _uiState.value.copy(address = value, errorMessage = null)
    }

    fun onEmergencyContactChange(value: String) {
        _uiState.value = _uiState.value.copy(emergencyContact = value, errorMessage = null)
    }

    fun submit() {
        val uid = authRepository.currentUid() ?: return
        val state = _uiState.value
        _uiState.value = state.copy(isWorking = true, errorMessage = null)
        viewModelScope.launch {
            try {
                authRepository.updateProfile(
                    uid = uid,
                    fullName = state.fullName,
                    phone = state.phone,
                    dob = state.dob,
                    address = state.address,
                    emergencyContact = state.emergencyContact,
                    clearProfileUpdateRequired = state.wasProfileUpdateRequired,
                )
                _uiState.value = _uiState.value.copy(isWorking = false, succeeded = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    errorMessage = friendlyMessage(e, "Couldn't save your profile. Please try again."),
                )
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
