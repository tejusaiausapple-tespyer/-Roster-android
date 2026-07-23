package com.surainvestments.roster.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.data.remote.SendNotificationRequest
import com.surainvestments.roster.data.remote.WorkerApiService
import com.surainvestments.roster.domain.model.Fix
import com.surainvestments.roster.domain.model.Shift
import com.surainvestments.roster.domain.model.ShiftAttendance
import java.time.Instant
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

/** `shift_attendance` collection — the Android analogue of iOS `RosterRepository`'s attendance listener. */
@Singleton
class ShiftAttendanceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val workerApi: WorkerApiService,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val staffFlowCache = ConcurrentHashMap<String, StateFlow<List<ShiftAttendance>>>()
    private val staffIndexCache = ConcurrentHashMap<String, StateFlow<Map<String, ShiftAttendance>>>()

    /** Live list of every verified clock-in/out record on [dateKey] (yyyy-MM-dd), any staff member. */
    fun attendanceForDate(dateKey: String): Flow<List<ShiftAttendance>> = callbackFlow {
        val registration = firestore.collection("shift_attendance")
            .whereEqualTo("date", dateKey)
            .addSnapshotListener { snapshot, _ ->
                val records = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { ShiftAttendance.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(records)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Live list of [staffId]'s own verified attendance records — a pure equality query on
     * `staffId`, so it needs no composite index. Matches the `shift_attendance` rules' own
     * read scope exactly (own records only, see `IOS-STAFF-AUDIT.md` §17).
     */
    fun attendanceForStaff(staffId: String): Flow<List<ShiftAttendance>> = callbackFlow {
        val registration = firestore.collection("shift_attendance")
            .whereEqualTo("staffId", staffId)
            .addSnapshotListener { snapshot, _ ->
                val records = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { ShiftAttendance.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(records)
            }
        awaitClose { registration.remove() }
    }

    /** Shared, cached version of [attendanceForStaff] — one listener shared across every collector. */
    fun staffAttendance(staffId: String): StateFlow<List<ShiftAttendance>> =
        staffFlowCache.getOrPut(staffId) {
            attendanceForStaff(staffId).stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    /** O(1) attendance-by-shift-id lookup for [staffId], mirroring iOS's `attendanceByShiftId` index. */
    fun staffAttendanceByShiftId(staffId: String): StateFlow<Map<String, ShiftAttendance>> =
        staffIndexCache.getOrPut(staffId) {
            staffAttendance(staffId)
                .map { records -> records.associateBy { it.shiftId } }
                .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
        }

    /**
     * Clock in: merge-creates `shift_attendance/{shiftId}` with a server-stamped `clockInAt` (the
     * rules reject anything else) plus the paired device timestamp and GPS [fix]. `clockInDeviceAt`
     * uses the device clock deliberately — it exists specifically to be compared against the
     * server timestamp, so it must be the device's own idea of "now".
     */
    suspend fun startShift(shift: Shift, staffId: String, fix: Fix?) {
        val fields = mutableMapOf<String, Any>(
            "shiftId" to shift.id,
            "staffId" to staffId,
            "date" to shift.date,
            "clockInAt" to FieldValue.serverTimestamp(),
            "clockInDeviceAt" to Timestamp(Date.from(Instant.now())),
        )
        shift.location?.let { fields["location"] = it }
        fields.putAll(ShiftAttendance.fixFields(prefix = "clockIn", fix = fix))
        firestore.collection("shift_attendance").document(shift.id).set(fields, SetOptions.merge()).await()
        notifyBestEffort(event = "shift-started", shiftId = shift.id)
    }

    /** Clock out: merges the server-stamped `clockOutAt`, paired device timestamp, GPS [fix], and optional early-leave [note]. */
    suspend fun endShift(shift: Shift, fix: Fix?, note: String?) {
        val fields = mutableMapOf<String, Any>(
            "clockOutAt" to FieldValue.serverTimestamp(),
            "clockOutDeviceAt" to Timestamp(Date.from(Instant.now())),
        )
        note?.trim()?.takeIf { it.isNotEmpty() }?.let { fields["clockOutNote"] = it.take(500) }
        fields.putAll(ShiftAttendance.fixFields(prefix = "clockOut", fix = fix))
        firestore.collection("shift_attendance").document(shift.id).set(fields, SetOptions.merge()).await()
        notifyBestEffort(event = "shift-ended", shiftId = shift.id)
    }

    private suspend fun notifyBestEffort(event: String, shiftId: String) {
        runCatching { workerApi.sendNotification(SendNotificationRequest(event = event, shiftIds = listOf(shiftId))) }
    }
}
