package com.surainvestments.roster.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.surainvestments.roster.data.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * `settings/availabilityLocks` — manager-published week locks, readable by any authenticated
 * user (see `firestore.rules`'s generic `settings/{docId}` rule). Only entries whose value is
 * `true` count as locked, guarding against stray falsy values. Mirrors iOS
 * `RosterRepository.lockedAvailabilityWeeks`.
 */
@Singleton
class AvailabilityLocksRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private fun lockedWeeksFlow(): Flow<Set<String>> = callbackFlow {
        val registration = firestore.collection("settings").document("availabilityLocks")
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val weeks = snapshot?.data?.get("weeks") as? Map<String, Any?> ?: emptyMap()
                trySend(weeks.filterValues { it == true }.keys)
            }
        awaitClose { registration.remove() }
    }

    /** Shared, cached set of manager-locked future week keys — one listener for the whole app. */
    val lockedWeeks: StateFlow<Set<String>> by lazy {
        lockedWeeksFlow().stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    }
}
