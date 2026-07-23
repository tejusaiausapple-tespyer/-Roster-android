package com.surainvestments.roster.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.domain.model.AppSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** `settings/app` — a single global singleton document, shared by every company user. */
@Singleton
class AppSettingsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun appSettingsFlow(): Flow<AppSettings> = callbackFlow {
        val registration = firestore.collection("settings").document("app")
            .addSnapshotListener { snapshot, _ ->
                val settings = snapshot?.data?.let { AppSettings.fromDocument(it) } ?: AppSettings.Fallback
                trySend(settings)
            }
        awaitClose { registration.remove() }
    }

    /** Shared, cached version of [appSettingsFlow] — one listener for the whole app. */
    val appSettings: StateFlow<AppSettings> by lazy {
        appSettingsFlow().stateIn(appScope, SharingStarted.WhileSubscribed(5_000), AppSettings.Fallback)
    }
}
