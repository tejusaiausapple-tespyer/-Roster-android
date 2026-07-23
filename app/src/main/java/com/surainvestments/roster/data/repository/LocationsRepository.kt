package com.surainvestments.roster.data.repository

import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.domain.model.RosterLocation
import com.surainvestments.roster.domain.model.Shift
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** `settings/locations` — manager-defined work locations, readable by any authenticated user. */
@Singleton
class LocationsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private fun locationsFlow(): Flow<List<RosterLocation>> = callbackFlow {
        val registration = firestore.collection("settings").document("locations")
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val items = snapshot?.data?.get("items") as? List<Map<String, Any?>> ?: emptyList()
                trySend(items.mapNotNull { RosterLocation.fromMap(it) }.sortedBy { it.displayName })
            }
        awaitClose { registration.remove() }
    }

    /** Shared, cached list of every saved work location — one listener for the whole app. */
    val locations: StateFlow<List<RosterLocation>> by lazy {
        locationsFlow().stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /** The workplace [shift] was rostered at, if it has a saved geofence anchor. Mirrors iOS `workplace(for:)`. */
    fun workplace(shift: Shift, locations: List<RosterLocation> = this.locations.value): RosterLocation? =
        locations.firstOrNull { it.displayName == shift.location && it.hasGeofence }
}
