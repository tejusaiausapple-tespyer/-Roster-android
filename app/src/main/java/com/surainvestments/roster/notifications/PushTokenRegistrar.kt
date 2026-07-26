package com.surainvestments.roster.notifications

import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.NotificationTokenRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Registers/refreshes the FCM token on every login — the Android analogue of iOS
 * `NotificationService.requestAuthorizationAndRegister()` being called on every sign-in
 * (`ANDROID-STAFF-BUILD-PLAN.md` §5). Started once from
 * [com.surainvestments.roster.RosterApplication]; no-ops while signed out.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationTokenRepository: NotificationTokenRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun start() {
        appScope.launch {
            authRepository.authStateFlow().collect { uid ->
                if (uid != null) {
                    runCatching { notificationTokenRepository.registerCurrentToken(uid) }
                }
            }
        }
    }
}
