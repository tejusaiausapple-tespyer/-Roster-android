package com.surainvestments.roster.ui.auth

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surainvestments.roster.data.local.DeviceAuthPreferences
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.AuthError
import com.surainvestments.roster.domain.model.UserStatus
import com.surainvestments.roster.domain.routing.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mirrors iOS's `AppConfig.deviceAuthBackgroundRelock` (2 minutes). */
private const val DEVICE_AUTH_RELOCK_MS = 2 * 60 * 1000L

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val forcedSignOutMessage: String? = null,
)

/**
 * Owns the session/routing state machine — the Android analogue of iOS's
 * `AuthViewModel` (ViewModels/AuthViewModel.swift) + `RootView`'s mid-session
 * forced-sign-out watcher. A single instance is shared across the nav graph
 * (Hilt ViewModels scoped to the hosting Activity are singletons for its
 * lifetime), matching iOS's single `@Observable AuthViewModel` in the
 * environment.
 *
 * Implements [DefaultLifecycleObserver] on the *process* lifecycle (not the
 * Activity's) so the 2-minute re-lock threshold matches iOS's scenePhase
 * handling — backgrounding the whole app, not just rotating/navigating.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val deviceAuthPreferences: DeviceAuthPreferences,
) : ViewModel(), DefaultLifecycleObserver {

    private val isRestoring = MutableStateFlow(true)

    private val uid: StateFlow<String?> = authRepository.authStateFlow()
        .onEach { isRestoring.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentUser: StateFlow<AppUser?> = uid
        .flatMapLatest { currentUid -> currentUid?.let(authRepository::userProfileFlow) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The signed-in user's own live profile — e.g. for the Account tab's self-service deletion section. */
    val ownProfile: StateFlow<AppUser?> = currentUser

    private val deviceAuthEnabled = MutableStateFlow(false)
    val isDeviceAuthEnabled: StateFlow<Boolean> = deviceAuthEnabled
    private val deviceAuthVerified = MutableStateFlow(false)

    /** Set just before an interactive login call — a fresh login skips the device-auth gate for this session. */
    private var pendingFreshLogin = false
    private var backgroundedAtMillis: Long? = null

    val route: StateFlow<AppRoute> = combine(
        isRestoring,
        uid,
        currentUser,
        deviceAuthEnabled,
        deviceAuthVerified,
    ) { restoring, currentUid, user, authEnabled, authVerified ->
        AppRoute.determine(
            isRestoring = restoring,
            uid = currentUid,
            user = user,
            deviceAuthEnabled = authEnabled,
            deviceAuthVerified = authVerified,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppRoute.Restoring)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        // Mid-session forced sign-out: if the account is locked/deactivated while
        // signed in (e.g. a manager locks it from another device), sign out
        // immediately rather than leaving a half-valid session on screen.
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null && uid.value != null) {
                    when (user.status) {
                        UserStatus.Locked -> forceSignOut(AuthError.AccountLocked.message)
                        UserStatus.Inactive -> forceSignOut(AuthError.AccountInactive.message)
                        UserStatus.Active -> Unit
                    }
                }
            }
        }

        viewModelScope.launch {
            uid.collect { currentUid ->
                deviceAuthEnabled.value = currentUid != null && deviceAuthPreferences.isEnabled(currentUid)
                when {
                    currentUid == null -> deviceAuthVerified.value = false
                    pendingFreshLogin -> {
                        deviceAuthVerified.value = true
                        pendingFreshLogin = false
                    }
                }
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAtMillis = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        val backgroundedAt = backgroundedAtMillis
        backgroundedAtMillis = null
        if (backgroundedAt == null || !deviceAuthEnabled.value) return
        if (System.currentTimeMillis() - backgroundedAt >= DEVICE_AUTH_RELOCK_MS) {
            deviceAuthVerified.value = false
        }
    }

    fun markDeviceAuthVerified() {
        deviceAuthVerified.value = true
    }

    /** Called after the Account screen's own biometric-prompt enable/disable flow succeeds. */
    fun setDeviceAuthEnabled(enabled: Boolean) {
        val currentUid = uid.value ?: return
        deviceAuthPreferences.setEnabled(currentUid, enabled)
        deviceAuthEnabled.value = enabled
        if (enabled) deviceAuthVerified.value = true
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun login() {
        val email = _uiState.value.email
        val password = _uiState.value.password
        _uiState.value = _uiState.value.copy(isWorking = true, errorMessage = null, forcedSignOutMessage = null)
        pendingFreshLogin = true
        viewModelScope.launch {
            try {
                authRepository.signIn(email, password)
            } catch (e: AuthError) {
                pendingFreshLogin = false
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isWorking = false)
            }
        }
    }

    fun logout() {
        authRepository.signOut()
    }

    private fun forceSignOut(message: String) {
        authRepository.signOut()
        _uiState.value = _uiState.value.copy(forcedSignOutMessage = message)
    }
}
