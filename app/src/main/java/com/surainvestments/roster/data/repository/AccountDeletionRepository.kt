package com.surainvestments.roster.data.repository

import com.surainvestments.roster.data.remote.AccountDeletionRequestBody
import com.surainvestments.roster.data.remote.WorkerApiService
import com.surainvestments.roster.data.remote.bodyOrThrow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ATO-safe account deletion lifecycle — Android analogue of iOS's
 * `RosterRepository` deletion functions (Services/RosterRepository.swift).
 * The staff request is a thin Worker call; the reactive `deletion` state on
 * [com.surainvestments.roster.domain.model.AppUser] (from the live Firestore
 * listener) is what actually drives the UI, not these calls' return values.
 */
@Singleton
class AccountDeletionRepository @Inject constructor(
    private val workerApi: WorkerApiService,
) {
    /**
     * Staff self-request. `via` should be "android", but the Worker's current
     * whitelist (`worker/handlers/accountDeletion.ts`) only recognizes
     * "ios"/"pwa"/"support" and silently coerces anything else to "pwa" —
     * a known cross-app gap (Android isn't in the via whitelist yet), not an
     * Android-side bug. Harmless: it only affects the audit-trail label.
     */
    suspend fun requestOwnAccountDeletion() {
        workerApi.requestAccountDeletion(AccountDeletionRequestBody(via = "android")).bodyOrThrow()
    }

}
