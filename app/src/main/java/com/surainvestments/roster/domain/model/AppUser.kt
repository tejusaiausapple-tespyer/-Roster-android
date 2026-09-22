package com.surainvestments.roster.domain.model

/**
 * Staff-owned `users/{uid}` profile fields used by the Android product.
 */
data class AppUser(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val employeeId: String?,
    val role: UserRole,
    val status: UserStatus,
    val mustChangePassword: Boolean,
    val dob: String?,
    val address: String?,
    val defaultLocation: String?,
    val profileUpdateRequired: Boolean,
    val emailChangeRequired: Boolean,
    val employmentType: EmploymentType?,
    val startDate: String?,
    val createdAt: String?,
    val deletion: AccountDeletionState?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
    val emergencyContactAddress: String?,
    val emergencyContactEmail: String?,
    /** Staff's own "template" week — read-only from the staff portal, never staff-writable. */
    val availability: UserAvailability?,
    /** Keyed by Monday `yyyy-MM-dd` week-start. Written only via the Worker (server-enforced week lock). */
    val weeklyAvailability: Map<String, UserAvailability>,
) {
    /** Mirrors `AppUser.needsProfileCompletion` — staff-only; managers never gate on this. */
    val needsProfileCompletion: Boolean
        get() {
            if (role != UserRole.Staff) return false
            val complete = !dob.isNullOrEmpty() && !address.isNullOrEmpty() && !phone.isNullOrEmpty()
            return !complete || profileUpdateRequired
        }

    /** Mirrors iOS `AppUser.initials`. */
    val initials: String
        get() {
            val letters = fullName
                .split(' ')
                .filter { it.isNotBlank() }
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            return letters.joinToString("").ifBlank { "?" }
        }

    /**
     * Display string for "Member Since" — prefers `startDate`, falls back to
     * `createdAt` month/year (iOS `memberSince`).
     */
    val memberSince: String?
        get() {
            val raw = startDate?.takeIf { it.isNotBlank() } ?: createdAt?.takeIf { it.isNotBlank() }
            return raw?.let { formatMonthYear(it) }
        }

    companion object {
        /**
         * Tolerant parse from a Firestore document map — coerces booleans that
         * may arrive as non-Boolean types, matching the `FS` helper pattern
         * both other clients rely on (see iOS `FirestoreValue.swift`).
         */
        @Suppress("UNCHECKED_CAST")
        fun fromDocument(id: String, data: Map<String, Any?>): AppUser =
            AppUser(
                id = id,
                fullName = data["fullName"] as? String ?: "",
                email = data["email"] as? String ?: "",
                phone = data["phone"] as? String,
                employeeId = data["employeeId"] as? String,
                role = UserRole.fromRaw(data["role"] as? String),
                status = UserStatus.fromRaw(data["status"] as? String),
                mustChangePassword = data["mustChangePassword"] as? Boolean ?: false,
                dob = data["dob"] as? String,
                address = data["address"] as? String,
                defaultLocation = data["defaultLocation"] as? String,
                profileUpdateRequired = data["profileUpdateRequired"] as? Boolean ?: false,
                emailChangeRequired = data["emailChangeRequired"] as? Boolean ?: false,
                employmentType = EmploymentType.fromRaw(data["employmentType"] as? String),
                startDate = data["startDate"] as? String,
                createdAt = data["createdAt"] as? String,
                deletion = AccountDeletionState.fromMap(data["deletion"] as? Map<String, Any?>),
                emergencyContactName = (data["emergencyContactName"] as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?: (data["emergencyContact"] as? String),
                emergencyContactPhone = data["emergencyContactPhone"] as? String,
                emergencyContactAddress = data["emergencyContactAddress"] as? String,
                emergencyContactEmail = data["emergencyContactEmail"] as? String,
                availability = (data["availability"] as? Map<String, Any?>)?.let { UserAvailability.fromMap(it) },
                weeklyAvailability = (data["weeklyAvailability"] as? Map<String, Any?>)
                    ?.mapNotNull { (weekKey, value) ->
                        (value as? Map<String, Any?>)?.let { weekKey to UserAvailability.fromMap(it) }
                    }
                    ?.toMap()
                    ?: emptyMap(),
            )

        private fun formatMonthYear(isoOrDate: String): String {
            // Accept "YYYY-MM-DD…" or already-friendly strings; keep display simple.
            val parts = isoOrDate.take(10).split('-')
            if (parts.size >= 2) {
                val year = parts[0]
                val month = parts[1].toIntOrNull()
                val names = listOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
                )
                if (month != null && month in 1..12) return "${names[month - 1]} $year"
            }
            return isoOrDate.take(10)
        }
    }
}
