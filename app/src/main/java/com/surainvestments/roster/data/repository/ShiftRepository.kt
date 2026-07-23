package com.surainvestments.roster.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.domain.model.RosterCalendar
import com.surainvestments.roster.domain.model.Shift
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

/** `shifts` collection — the Android analogue of iOS `RosterRepository`'s shift listeners. */
@Singleton
class ShiftRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    // Keyed by staffId. Memoized so every staff screen (Home, Roster, Tasks...) collecting the
    // same staff member's window shares one underlying Firestore listener instead of each
    // ViewModel opening its own — matches iOS's single-`RosterRepository`-instance model.
    private val staffWindowCache = ConcurrentHashMap<String, StateFlow<List<Shift>>>()
    private val staffIndexCache = ConcurrentHashMap<String, StateFlow<Map<String, Shift>>>()

    /** Live list of every shift (any staff) rostered on [dateKey] (yyyy-MM-dd). */
    fun shiftsForDate(dateKey: String): Flow<List<Shift>> = callbackFlow {
        val registration = firestore.collection("shifts")
            .whereEqualTo("date", dateKey)
            .addSnapshotListener { snapshot, _ ->
                val shifts = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Shift.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(shifts)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Live list of [staffId]'s published shifts within [startDateKey]..[endDateKey] (inclusive,
     * yyyy-MM-dd). Matches the deployed `shifts` composite index (staffId, status, date) exactly —
     * see `firestore.indexes.json` in the PWA repo — so no backend change is needed.
     */
    fun publishedShiftsForStaff(
        staffId: String,
        startDateKey: String,
        endDateKey: String,
    ): Flow<List<Shift>> = callbackFlow {
        val registration = firestore.collection("shifts")
            .whereEqualTo("staffId", staffId)
            .whereEqualTo("status", "published")
            .whereGreaterThanOrEqualTo("date", startDateKey)
            .whereLessThanOrEqualTo("date", endDateKey)
            .addSnapshotListener { snapshot, _ ->
                val shifts = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Shift.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(shifts)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Shared, cached −28/+56 day window of [staffId]'s own published shifts — the single
     * source every staff screen should collect instead of calling [publishedShiftsForStaff]
     * directly, so Home/Roster/Tasks etc. share one underlying Firestore listener rather than
     * each opening its own on the same query. Matches iOS's shift-visibility window exactly
     * (`IOS-STAFF-AUDIT.md` §5).
     */
    fun staffShiftsWindow(staffId: String): StateFlow<List<Shift>> =
        staffWindowCache.getOrPut(staffId) {
            val startKey = RosterCalendar.dateKey(-28)
            val endKey = RosterCalendar.dateKey(56)
            publishedShiftsForStaff(staffId, startKey, endKey)
                .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    /** O(1) shift lookup by id within [staffShiftsWindow], mirroring iOS's `shiftsById` index. */
    fun staffShiftsById(staffId: String): StateFlow<Map<String, Shift>> =
        staffIndexCache.getOrPut(staffId) {
            staffShiftsWindow(staffId)
                .map { shifts -> shifts.associateBy { it.id } }
                .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
        }
}
