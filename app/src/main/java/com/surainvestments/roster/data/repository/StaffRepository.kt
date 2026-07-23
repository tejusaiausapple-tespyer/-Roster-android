package com.surainvestments.roster.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.surainvestments.roster.data.remote.ChangeStaffEmailRequest
import com.surainvestments.roster.data.remote.CreateAuthUserRequest
import com.surainvestments.roster.data.remote.ResetStaffPasswordRequest
import com.surainvestments.roster.data.remote.WorkerApiService
import com.surainvestments.roster.data.remote.bodyOrThrow
import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.EmploymentType
import com.surainvestments.roster.domain.model.UserStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager staff-account management — the Android analogue of iOS's
 * `RosterRepository` "Manager staff management" section
 * (Services/RosterRepository.swift). Account creation and password reset go
 * through the Worker (privileged Firebase Auth Admin operations); status
 * changes (lock/unlock/deactivate/reactivate) are direct Firestore writes,
 * matching the same manager-elevated Firestore rules both other clients rely
 * on. Every mutation writes a client-side `auditLogs` entry in the shape both
 * other clients read, so the audit trail stays consistent across all three apps.
 */
@Singleton
class StaffRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val workerApi: WorkerApiService,
) {

    /** Live list of every staff (role == "staff") user doc, manager-scoped. */
    fun staffListFlow(): Flow<List<AppUser>> = callbackFlow {
        val registration = firestore.collection("users")
            .whereEqualTo("role", "staff")
            .addSnapshotListener { snapshot, _ ->
                val staff = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { AppUser.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(staff)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Create a staff Firebase Auth account (Worker) then the `users/{uid}`
     * profile doc (direct Firestore write) — mirrors iOS's `createStaff`
     * exactly, including the forced-password-change default and the
     * CREATE_USER audit entry.
     */
    suspend fun createStaff(
        fullName: String,
        email: String,
        password: String,
        employmentType: EmploymentType,
        phone: String?,
        startDate: String?,
        actorUid: String,
    ): String {
        val localId = workerApi.createAuthUser(CreateAuthUserRequest(email, password)).bodyOrThrow().localId
        val now = Instant.now().toString()
        val data = mapOf(
            "id" to localId,
            "fullName" to fullName,
            "email" to email,
            "phone" to (phone ?: ""),
            "role" to "staff",
            "employmentType" to employmentType.rawValue,
            "startDate" to (startDate ?: ""),
            "mustChangePassword" to true,
            "status" to UserStatus.Active.rawValue,
            "createdAt" to now,
            "updatedAt" to now,
        )
        firestore.collection("users").document(localId).set(data).await()
        writeAuditLog(actorUid, "CREATE_USER", localId)
        return localId
    }

    /** Lock, unlock, deactivate, or reactivate — a plain `status` field write plus an audit entry. */
    suspend fun setStaffStatus(staffId: String, status: UserStatus, actorUid: String) {
        firestore.collection("users").document(staffId)
            .update(mapOf("status" to status.rawValue, "updatedAt" to Instant.now().toString()))
            .await()
        val action = when (status) {
            UserStatus.Locked -> "LOCK_USER"
            UserStatus.Inactive -> "DEACTIVATE_USER"
            UserStatus.Active -> "REACTIVATE_USER"
        }
        writeAuditLog(actorUid, action, staffId)
    }

    suspend fun resetStaffPassword(staffId: String, temporaryPassword: String) {
        workerApi.resetStaffPassword(ResetStaffPasswordRequest(staffId, temporaryPassword)).bodyOrThrow()
    }

    suspend fun changeStaffEmail(staffId: String, newEmail: String, managerPassword: String) {
        workerApi.changeStaffEmail(ChangeStaffEmailRequest(staffId, newEmail, managerPassword)).bodyOrThrow()
    }

    /**
     * Partial update of a staff member's profile fields (name, phone, employee ID, TFN,
     * employment type, status, start date, DOB, emergency contact). Mirrors iOS
     * `RosterRepository.updateStaffFields` — a direct Firestore write under the same
     * manager-elevated rules used for shifts/timesheets. Email is never edited here —
     * it's a sign-in credential; see [requestStaffEmailChange].
     */
    suspend fun updateStaffFields(staffId: String, fields: Map<String, Any>, actorUid: String) {
        if (fields.isEmpty()) return
        val data = fields + ("updatedAt" to Instant.now().toString())
        firestore.collection("users").document(staffId).update(data).await()
        writeAuditLog(actorUid, "UPDATE_USER", staffId)
    }

    /**
     * Prompt a staff member to change their own sign-in email — sets a flag on their
     * user doc; the staff app shows a banner and they complete the change themselves
     * via Firebase's verified flow (only the user can change their own Auth email
     * securely without a password prompt). Mirrors iOS's `requestStaffEmailChange`.
     */
    suspend fun requestStaffEmailChange(staffId: String, actorUid: String) {
        firestore.collection("users").document(staffId)
            .update(mapOf("emailChangeRequired" to true, "updatedAt" to Instant.now().toString()))
            .await()
        writeAuditLog(actorUid, "REQUEST_EMAIL_CHANGE", staffId)
    }

    suspend fun cancelStaffEmailChange(staffId: String, actorUid: String) {
        firestore.collection("users").document(staffId)
            .update(mapOf("emailChangeRequired" to false, "updatedAt" to Instant.now().toString()))
            .await()
        writeAuditLog(actorUid, "CANCEL_EMAIL_CHANGE_REQUEST", staffId)
    }

    /**
     * Clears the stored address and requires a new one on next login (staff won't reach
     * their dashboard until they re-enter it). Mirrors iOS's `requestStaffAddressUpdate`.
     */
    suspend fun requestStaffAddressUpdate(staffId: String, actorUid: String) {
        firestore.collection("users").document(staffId)
            .update(
                mapOf(
                    "address" to "",
                    "profileUpdateRequired" to true,
                    "updatedAt" to Instant.now().toString(),
                ),
            )
            .await()
        writeAuditLog(actorUid, "REQUIRE_NEW_ADDRESS", staffId)
    }

    private suspend fun writeAuditLog(actorId: String, action: String, entityId: String) {
        val auditId = UUID.randomUUID().toString().replace("-", "")
        runCatching {
            firestore.collection("auditLogs").document(auditId).set(
                mapOf(
                    "id" to auditId,
                    "actorUserId" to actorId,
                    "action" to action,
                    "entityType" to "user",
                    "entityId" to entityId,
                    "createdAt" to Instant.now().toString(),
                ),
            ).await()
        }
    }
}
