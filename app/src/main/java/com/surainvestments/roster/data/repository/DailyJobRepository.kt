package com.surainvestments.roster.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.data.remote.SendNotificationRequest
import com.surainvestments.roster.data.remote.WorkerApiService
import com.surainvestments.roster.domain.model.DailyJobAssignment
import com.surainvestments.roster.domain.model.RosterCalendar
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

/** `daily_job_assignments` collection — the Android analogue of iOS `RosterRepository`'s daily-job listener. */
@Singleton
class DailyJobRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val workerApi: WorkerApiService,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val staffTodayCache = ConcurrentHashMap<String, StateFlow<List<DailyJobAssignment>>>()
    private val staffWindowCache = ConcurrentHashMap<String, StateFlow<List<DailyJobAssignment>>>()

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

    /**
     * [staffId]'s own Daily Job assignments for [dateKey] — visible for the entire shift date
     * regardless of rostered end time, disappearing once the date rolls over (a fresh query the
     * next day, not a live rollover — matches iOS's `isVisibleToStaff`, `IOS-FEATURE-INVENTORY.md` §10).
     */
    fun assignmentsForStaffOnDate(staffId: String, dateKey: String): Flow<List<DailyJobAssignment>> = callbackFlow {
        val registration = firestore.collection("daily_job_assignments")
            .whereEqualTo("staffId", staffId)
            .whereEqualTo("date", dateKey)
            .addSnapshotListener { snapshot, _ ->
                val assignments = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { DailyJobAssignment.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(assignments)
            }
        awaitClose { registration.remove() }
    }

    /** Shared, cached version of [assignmentsForStaffOnDate] for today — Home's badge and the bell panel both collect this one listener. */
    fun todaysAssignments(staffId: String, todayKey: String): StateFlow<List<DailyJobAssignment>> =
        staffTodayCache.getOrPut("$staffId:$todayKey") {
            assignmentsForStaffOnDate(staffId, todayKey).stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    /**
     * [staffId]'s assignments across the whole shift window (same ±28/56-day bound as
     * [com.surainvestments.roster.data.repository.ShiftRepository.staffShiftsWindow]), NOT scoped
     * to a single day. For a long-lived subscriber (DailyJobReminderScheduler, an
     * Application-singleton with no screen lifecycle to naturally re-subscribe it) — a
     * single-day-scoped query like [todaysAssignments] silently goes stale the moment local
     * midnight passes, since the Firestore `date` filter it was built with never changes on its
     * own. This wide window stays valid for weeks, so the caller can re-derive "today" fresh on
     * every emission instead.
     */
    fun assignmentsForStaffWindow(staffId: String): Flow<List<DailyJobAssignment>> =
        staffWindowCache.getOrPut(staffId) {
            val startKey = RosterCalendar.dateKey(-28)
            val endKey = RosterCalendar.dateKey(56)
            assignmentsForStaffInRange(staffId, startKey, endKey)
                .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    private fun assignmentsForStaffInRange(staffId: String, startKey: String, endKey: String): Flow<List<DailyJobAssignment>> = callbackFlow {
        val registration = firestore.collection("daily_job_assignments")
            .whereEqualTo("staffId", staffId)
            .whereGreaterThanOrEqualTo("date", startKey)
            .whereLessThanOrEqualTo("date", endKey)
            .addSnapshotListener { snapshot, _ ->
                val assignments = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { DailyJobAssignment.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(assignments)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Toggles one assignment's completion — writes **only** `completed/completedAt/completedBy`
     * via a partial `update`, never a full-object `set` (the rules' `hasOnly([...])` check rejects
     * anything else — `ANDROID-STAFF-BUILD-PLAN.md` Phase G). [siblings] is the already-loaded
     * today's-assignments list the caller has in hand; used only to detect "last job for this
     * shift just completed" for the best-effort `jobs-all-completed` push, never re-fetched.
     */
    suspend fun setCompleted(assignment: DailyJobAssignment, staffId: String, completed: Boolean, siblings: List<DailyJobAssignment>) {
        val fields = mapOf(
            "completed" to completed,
            "completedAt" to if (completed) FieldValue.serverTimestamp() else null,
            "completedBy" to if (completed) staffId else null,
        )
        firestore.collection("daily_job_assignments").document(assignment.id).update(fields).await()

        if (completed) {
            val allDoneForShift = siblings
                .filter { it.shiftId == assignment.shiftId }
                .all { it.id == assignment.id || it.completed }
            if (allDoneForShift) {
                runCatching {
                    workerApi.sendNotification(SendNotificationRequest(event = "jobs-all-completed", shiftIds = listOf(assignment.shiftId)))
                }
            }
        }
    }
}
