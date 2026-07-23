package com.surainvestments.roster.domain.model

/** Mirrors `UserRole` in the iOS/PWA `users/{uid}` schema. Raw values are the exact Firestore field values. */
enum class UserRole(val rawValue: String) {
    Manager("manager"),
    Staff("staff"),
    ;

    companion object {
        fun fromRaw(value: String?): UserRole = entries.firstOrNull { it.rawValue == value } ?: Staff
    }
}

/** Mirrors `UserStatus`. */
enum class UserStatus(val rawValue: String) {
    Active("active"),
    Inactive("inactive"),
    Locked("locked"),
    ;

    companion object {
        fun fromRaw(value: String?): UserStatus = entries.firstOrNull { it.rawValue == value } ?: Active
    }
}

/** Mirrors `EmploymentType`. */
enum class EmploymentType(val rawValue: String, val label: String) {
    FullTime("full_time", "Full-time"),
    PartTime("part_time", "Part-time"),
    Casual("casual", "Casual"),
    ;

    companion object {
        fun fromRaw(value: String?): EmploymentType? = entries.firstOrNull { it.rawValue == value }
    }
}
