package com.surainvestments.roster.data.repository

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.surainvestments.roster.data.local.PayslipPreferences
import com.surainvestments.roster.domain.model.Payslip
import com.surainvestments.roster.domain.model.PayslipStatus
import com.surainvestments.roster.domain.model.RosterCalendar
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * `payslips` collection — staff-visible slice only, cache-first, **never a live listener**
 * (payslips are cold/append-only data, unlike shifts/timesheets). Mirrors iOS
 * `RosterRepository.staffPayslips(monthKey:)` exactly (`IOS-STAFF-AUDIT.md` §9):
 *
 * 1. Session memory (this singleton's own cache, for the process lifetime).
 * 2. Firestore's on-disk persistent cache (`Source.CACHE`, zero reads, offline-capable).
 * 3. The server (`Source.SERVER`) — only for a month never fetched on this device, the current
 *    month once per session, or an explicit `forceRefresh` (pull-to-refresh).
 *
 * The month-scoping query rides the `{periodStart}_{staffId}` document-id prefix as a range
 * bound alongside the `staffId`/`status` equality filters — no composite index needed, since
 * `__name__` terminates every Firestore index automatically. If that combination is ever
 * rejected server-side, falls back to the plain equality query and buckets client-side by
 * month, exactly matching iOS's own defensive fallback.
 */
@Singleton
class PayslipRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val payslipPreferences: PayslipPreferences,
) {
    private val monthCache = ConcurrentHashMap<String, List<Payslip>>()
    private val refreshedThisSession = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    suspend fun staffPayslips(staffId: String, monthKey: String, forceRefresh: Boolean = false): List<Payslip> {
        val cacheKey = "$staffId:$monthKey"
        val isCurrentMonth = monthKey == RosterCalendar.monthKey()
        val needsServer = forceRefresh || (isCurrentMonth && cacheKey !in refreshedThisSession)

        if (!needsServer) {
            monthCache[cacheKey]?.let { return it }
        }

        val query = staffPayslipMonthQuery(staffId, monthKey) ?: return emptyList()

        if (!needsServer) {
            val cached = runCatching { query.get(Source.CACHE).await() }.getOrNull()
            if (cached != null) {
                val slips = parseStaffPayslips(cached)
                if (slips.isNotEmpty() || payslipPreferences.isMonthDownloaded(staffId, monthKey)) {
                    monthCache[cacheKey] = slips
                    return slips
                }
            }
        }

        val slips = try {
            parseStaffPayslips(query.get(Source.SERVER).await())
        } catch (e: Exception) {
            val snap = firestore.collection("payslips")
                .whereEqualTo("staffId", staffId)
                .whereIn("status", STAFF_VISIBLE_STATUSES)
                .get(Source.SERVER)
                .await()
            val all = parseStaffPayslips(snap)
            val byMonth = all.groupBy { it.periodStart.take(7) }
            byMonth.forEach { (month, monthSlips) ->
                monthCache["$staffId:$month"] = monthSlips
                refreshedThisSession.add("$staffId:$month")
                payslipPreferences.markMonthDownloaded(staffId, month)
            }
            byMonth[monthKey] ?: emptyList()
        }

        monthCache[cacheKey] = slips
        refreshedThisSession.add(cacheKey)
        payslipPreferences.markMonthDownloaded(staffId, monthKey)
        return slips
    }

    private fun staffPayslipMonthQuery(staffId: String, monthKey: String): Query? {
        val (start, end) = RosterCalendar.monthDayKeyBounds(monthKey) ?: return null
        return firestore.collection("payslips")
            .whereEqualTo("staffId", staffId)
            .whereIn("status", STAFF_VISIBLE_STATUSES)
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), start)
            .whereLessThan(FieldPath.documentId(), end)
    }

    private fun parseStaffPayslips(snap: QuerySnapshot): List<Payslip> =
        snap.documents
            .mapNotNull { doc -> doc.data?.let { Payslip.fromDocument(doc.id, it) } }
            .filter { it.status.isStaffVisible }
            .sortedByDescending { it.periodStart }

    private companion object {
        val STAFF_VISIBLE_STATUSES = listOf(PayslipStatus.Submitted.rawValue, PayslipStatus.Archived.rawValue)
    }
}
