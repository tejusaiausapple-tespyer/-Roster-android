package com.surainvestments.roster.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.data.remote.WorkerApiService
import com.surainvestments.roster.data.remote.SendNotificationRequest
import com.surainvestments.roster.domain.model.BusinessRules
import com.surainvestments.roster.domain.model.Timesheet
import com.surainvestments.roster.domain.model.TimesheetStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

/** `timesheets` collection — the Android analogue of iOS `RosterRepository`'s manager timesheet listener. */
@Singleton
class TimesheetRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val workerApi: WorkerApiService,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val staffIndexCache = ConcurrentHashMap<String, StateFlow<Map<String, Timesheet>>>()
    /**
     * Live list of every staff member's timesheets submitted in the last 90 days —
     * mirrors iOS's manager `managerTimesheetCutoff` rolling window (all staff, no filter).
     */
    fun recentTimesheets(): Flow<List<Timesheet>> = callbackFlow {
        val cutoff = Timestamp(Date.from(Instant.now().minus(BusinessRules.managerTimesheetWindowDaysBack.toLong(), ChronoUnit.DAYS)))
        val registration = firestore.collection("timesheets")
            .whereGreaterThanOrEqualTo("submittedAt", cutoff)
            .addSnapshotListener { snapshot, _ ->
                val timesheets = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Timesheet.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(timesheets)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Live list of [staffId]'s own submitted timesheets in the last [BusinessRules.staffTimesheetCutoffDays]
     * (5 years) — matches the deployed `timesheets` composite index (staffId, submittedAt)
     * exactly, so no backend change is needed. Used to derive shift status on Roster/Home, and
     * to compute [HoursMetrics] (which needs the full history for accurate year/all-time totals).
     */
    fun timesheetsForStaff(staffId: String): Flow<List<Timesheet>> = callbackFlow {
        val cutoff = Timestamp(Date.from(Instant.now().minus(BusinessRules.staffTimesheetCutoffDays.toLong(), ChronoUnit.DAYS)))
        val registration = firestore.collection("timesheets")
            .whereEqualTo("staffId", staffId)
            .whereGreaterThanOrEqualTo("submittedAt", cutoff)
            .addSnapshotListener { snapshot, _ ->
                val timesheets = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Timesheet.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(timesheets)
            }
        awaitClose { registration.remove() }
    }

    /**
     * O(1) timesheet-by-shift-id lookup for [staffId] (doc id == shift id, 1:1), shared and
     * cached across every collector — mirrors iOS's `timesheetsByShiftId` index. Backed by the
     * same 5-year window as [timesheetsForStaff].
     */
    fun staffTimesheetsByShiftId(staffId: String): StateFlow<Map<String, Timesheet>> =
        staffIndexCache.getOrPut(staffId) {
            timesheetsForStaff(staffId)
                .map { timesheets -> timesheets.associateBy { it.shiftId } }
                .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
        }

    /**
     * First submission of worked hours for [shiftId] — doc id == shiftId (1:1), matching the
     * `isStaffTimesheetCreate` rule exactly: `id`/`shiftId` both equal the doc id, `submittedAt`
     * is the server timestamp (the rule rejects anything else), and the shift must already be
     * submittable (rostered end passed, or a verified clock-out exists).
     */
    suspend fun submitTimesheet(
        shiftId: String,
        staffId: String,
        actualStart: String,
        actualEnd: String,
        breakMinutes: Int,
        workedHours: Double,
        notes: String,
    ) {
        val data = mapOf(
            "id" to shiftId,
            "shiftId" to shiftId,
            "staffId" to staffId,
            "actualStart" to actualStart,
            "actualEnd" to actualEnd,
            "actualBreakMinutes" to breakMinutes,
            "workedHours" to workedHours,
            "staffNotes" to notes,
            "status" to TimesheetStatus.Pending.rawValue,
            "submittedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to Instant.now().toString(),
        )
        firestore.collection("timesheets").document(shiftId).set(data).await()
        notifyBestEffort(event = "timesheet-submitted", shiftId = shiftId, timesheetId = shiftId)
    }

    /** Resubmit a previously rejected/pending/draft timesheet — resets it to pending. */
    suspend fun resubmitTimesheet(
        id: String,
        actualStart: String,
        actualEnd: String,
        breakMinutes: Int,
        workedHours: Double,
        notes: String,
    ) {
        val data = mapOf(
            "actualStart" to actualStart,
            "actualEnd" to actualEnd,
            "actualBreakMinutes" to breakMinutes,
            "workedHours" to workedHours,
            "staffNotes" to notes,
            "status" to TimesheetStatus.Pending.rawValue,
            "rejectedReason" to null,
            "submittedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to Instant.now().toString(),
        )
        firestore.collection("timesheets").document(id).update(data).await()
        notifyBestEffort(event = "timesheet-submitted", shiftId = id, timesheetId = id)
    }

    /**
     * Report an absence for [shiftId] — creates or updates `timesheets/{shiftId}` with
     * `absent_reported`. Zero worked hours always, per the rules' absence-report invariant.
     */
    suspend fun reportAbsence(shiftId: String, staffId: String, existing: Timesheet?, reason: String) {
        val absenceFields = mutableMapOf<String, Any?>(
            "actualStart" to "",
            "actualEnd" to "",
            "actualBreakMinutes" to 0,
            "workedHours" to 0,
            "staffNotes" to reason,
            "status" to TimesheetStatus.AbsentReported.rawValue,
            "submittedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to Instant.now().toString(),
        )
        if (existing != null && existing.status == TimesheetStatus.Rejected) {
            absenceFields["rejectedReason"] = null
            firestore.collection("timesheets").document(existing.id).update(absenceFields).await()
        } else {
            absenceFields["id"] = shiftId
            absenceFields["shiftId"] = shiftId
            absenceFields["staffId"] = staffId
            firestore.collection("timesheets").document(shiftId).set(absenceFields).await()
        }
        notifyBestEffort(event = "timesheet-absent", shiftId = shiftId, timesheetId = shiftId)
    }

    /** Undo a self-reported absence — deletes the timesheet (only allowed while still `absent_reported`). */
    suspend fun undoAbsenceReport(timesheetId: String) {
        firestore.collection("timesheets").document(timesheetId).delete().await()
    }

    /** Best-effort push trigger — the Worker always returns HTTP 200 even on internal failure, so a thrown exception here means the network call itself failed; never let that fail the timesheet write it followed. */
    private suspend fun notifyBestEffort(event: String, shiftId: String, timesheetId: String) {
        runCatching {
            workerApi.sendNotification(SendNotificationRequest(event = event, shiftIds = listOf(shiftId), timesheetId = timesheetId))
        }
    }
}
