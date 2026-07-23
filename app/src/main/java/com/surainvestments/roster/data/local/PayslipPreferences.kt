package com.surainvestments.roster.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-uid record of which "yyyy-MM" payslip months this device has successfully fetched from
 * the server at least once — lets an empty Firestore-disk-cache result be trusted as "genuinely
 * no payslip that month" instead of "not downloaded yet." Mirrors iOS's
 * `payslipMonthsDownloaded.{uid}` UserDefaults key.
 */
@Singleton
class PayslipPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("payslip_months", Context.MODE_PRIVATE)

    fun isMonthDownloaded(uid: String, monthKey: String): Boolean =
        prefs.getStringSet(key(uid), emptySet())?.contains(monthKey) == true

    fun markMonthDownloaded(uid: String, monthKey: String) {
        val current = prefs.getStringSet(key(uid), emptySet()).orEmpty()
        if (monthKey in current) return
        prefs.edit { putStringSet(key(uid), current + monthKey) }
    }

    private fun key(uid: String) = "downloaded_months_$uid"
}
