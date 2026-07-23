package com.surainvestments.roster.domain.model

/** Mirrors iOS's `AccountDeletionStatus` (Models/AccountDeletionState.swift). */
enum class AccountDeletionStatus(val rawValue: String) {
    Requested("requested"),
    Approved("approved"),
    Cancelled("cancelled"),
    AuthPurged("auth_purged"),
    ;

    companion object {
        fun fromRaw(value: String?): AccountDeletionStatus? = entries.firstOrNull { it.rawValue == value }
    }
}

/** Mirrors iOS's `AccountDeletionState` — the ATO-safe deletion lifecycle on a `users/{uid}` doc. */
data class AccountDeletionState(
    val status: AccountDeletionStatus,
    val requestedAt: String?,
    val cancelDeadlineAt: String?,
) {
    companion object {
        fun fromMap(data: Map<String, Any?>?): AccountDeletionState? {
            if (data == null) return null
            val status = AccountDeletionStatus.fromRaw(data["status"] as? String) ?: return null
            return AccountDeletionState(
                status = status,
                requestedAt = data["requestedAt"] as? String,
                cancelDeadlineAt = data["cancelDeadlineAt"] as? String,
            )
        }
    }
}
