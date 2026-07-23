package com.surainvestments.roster.domain.model

/** `settings/app` — a single global singleton document. Mirrors iOS `AppSettings`. */
data class AppSettings(
    val companyName: String,
    val businessAddress: String,
    val businessStreet: String,
    val businessSuburb: String,
    val businessState: String,
    val businessCity: String,
    val contactPhone: String,
    val contactEmail: String,
    val abn: String,
    val acn: String,
    val businessNotes: String,
) {
    companion object {
        val Fallback = AppSettings(
            companyName = "Rosterra",
            businessAddress = "",
            businessStreet = "",
            businessSuburb = "",
            businessState = "",
            businessCity = "",
            contactPhone = "",
            contactEmail = "",
            abn = "",
            acn = "",
            businessNotes = "",
        )

        fun fromDocument(data: Map<String, Any?>): AppSettings =
            AppSettings(
                companyName = data.fsString("companyName")?.takeIf { it.isNotBlank() } ?: "Rosterra",
                businessAddress = data.fsString("businessAddress") ?: "",
                businessStreet = data.fsString("businessStreet") ?: "",
                businessSuburb = data.fsString("businessSuburb") ?: "",
                businessState = data.fsString("businessState") ?: "",
                businessCity = data.fsString("businessCity") ?: "",
                contactPhone = data.fsString("contactPhone") ?: "",
                contactEmail = data.fsString("contactEmail") ?: "",
                abn = data.fsString("abn") ?: "",
                acn = data.fsString("acn") ?: "",
                businessNotes = data.fsString("businessNotes") ?: "",
            )
    }
}
