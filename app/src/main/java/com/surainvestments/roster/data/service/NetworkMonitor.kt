package com.surainvestments.roster.data.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * App-wide connectivity signal for a lightweight offline indicator — **not** a retry/outbox
 * engine. The Firestore SDK's own offline cache and write queue already handle sync (matching
 * both source apps' deliberate "no bespoke sync engine" precedent, `ANDROID-STAFF-BUILD-PLAN.md`
 * §6); this exists purely so the UI can show "you're offline" rather than a screen that silently
 * looks broken.
 *
 * "Online" means the OS reports an internet-capable, *validated* network — a Wi-Fi radio that's
 * merely associated but stuck behind a captive portal, or has no real route to the internet,
 * correctly reads as offline rather than a false "online."
 */
@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext context: Context) {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    fun isOnlineFlow(): Flow<Boolean> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            // Fail open — never let a monitor that couldn't initialize block the rest of the app.
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        val validatedNetworks = mutableSetOf<Network>()
        fun emit() = trySend(validatedNetworks.isNotEmpty())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (validated) validatedNetworks.add(network) else validatedNetworks.remove(network)
                emit()
            }

            override fun onLost(network: Network) {
                validatedNetworks.remove(network)
                emit()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)

        val activeCapabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        trySend(
            activeCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                activeCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        )

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
