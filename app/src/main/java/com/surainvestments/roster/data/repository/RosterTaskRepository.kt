package com.surainvestments.roster.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.domain.model.RosterTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** `tasks` collection — the Android analogue of iOS `RosterRepository`'s active-tasks listener. */
@Singleton
class RosterTaskRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    /** Live list of every active task template (frequency/day/assignment filtering happens client-side, as on iOS). */
    fun activeTasks(): Flow<List<RosterTask>> = callbackFlow {
        val registration = firestore.collection("tasks")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("RosterTaskRepository", "activeTasks() listener failed", error)
                }
                val tasks = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { RosterTask.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(tasks)
            }
        awaitClose { registration.remove() }
    }

    /** Shared, cached version of [activeTasks] — one listener for the whole app. */
    val tasks: StateFlow<List<RosterTask>> by lazy {
        activeTasks().stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }
}
