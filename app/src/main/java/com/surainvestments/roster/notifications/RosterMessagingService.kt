package com.surainvestments.roster.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.NotificationTokenRepository
import com.surainvestments.roster.domain.model.FcmEventRouting
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM pushes and rotated tokens. The Worker sends a **data-only** payload (mirroring
 * iOS's need for an explicit `apns.payload.aps.alert` block for the same reason — see
 * `IOS-FEATURE-INVENTORY.md` §11) so there is no `notification` block for the system tray to
 * auto-display while backgrounded; every message is built and shown here explicitly, in every
 * app state, rather than assuming the OS handles it — the exact class of "silent push" gap the
 * plan calls out not to reintroduce.
 */
@AndroidEntryPoint
class RosterMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationTokenRepository: NotificationTokenRepository
    @Inject lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = authRepository.currentUid() ?: return
        serviceScope.launch { runCatching { notificationTokenRepository.registerToken(uid, token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val event = data["event"]
        val title = data["title"] ?: message.notification?.title ?: FcmEventRouting.defaultTitle(event)
        val body = data["body"] ?: message.notification?.body.orEmpty()
        val deepLink = FcmEventRouting.deepLink(event, data["shiftId"])
        val notificationId = (data["notificationId"] ?: event ?: title).hashCode()

        NotificationPoster.post(this, notificationId, title, body, FcmEventRouting.channelId(event), deepLink)
    }
}
