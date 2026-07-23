package com.surainvestments.roster.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.surainvestments.roster.domain.model.DailyJobAssignment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** `daily_job_assignments` collection — the Android analogue of iOS `RosterRepository`'s daily-job listener. */
@Singleton
class DailyJobRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /** Live list of every job assigned to a shift on [dateKey] (yyyy-MM-dd), any staff member. */
    fun assignmentsForDate(dateKey: String): Flow<List<DailyJobAssignment>> = callbackFlow {
        val registration = firestore.collection("daily_job_assignments")
            .whereEqualTo("date", dateKey)
            .addSnapshotListener { snapshot, _ ->
                val assignments = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { DailyJobAssignment.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(assignments)
            }
        awaitClose { registration.remove() }
    }
}
