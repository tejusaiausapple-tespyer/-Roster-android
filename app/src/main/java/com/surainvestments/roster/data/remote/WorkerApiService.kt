package com.surainvestments.roster.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Thin REST layer over the shared Cloudflare Worker's `/api` routes
 * (`worker/index.ts`) — the Android analogue of iOS's `WorkerAPIClient.swift`.
 * The Authorization header (bearer Firebase ID token) is attached
 * automatically by [FirebaseAuthInterceptor], not passed per-call.
 *
 * Every call returns a raw [Response] rather than throwing on non-2xx, since
 * several endpoints (e.g. rate limits, validation) return meaningful typed
 * error bodies ([ApiErrorResponse]) callers need to branch on — see
 * docs/ANDROID-BUILD-PLAN.md Phase 3.
 */
interface WorkerApiService {

    @GET("api/health")
    suspend fun health(): Response<HealthResponse>

    @POST("api/complete-password-change")
    suspend fun completePasswordChange(@Body body: EmptyRequestBody): Response<OkResponse>

    @POST("api/staff/availability")
    suspend fun saveStaffAvailability(@Body body: SaveStaffAvailabilityRequest): Response<SaveStaffAvailabilityResponse>

    @POST("api/send-notification")
    suspend fun sendNotification(@Body body: SendNotificationRequest): Response<SendNotificationResponse>

    @POST("api/notifications/activate-device")
    suspend fun activateDevice(@Body body: ActivateDeviceRequest): Response<ActivateDeviceResponse>

    @POST("api/account-deletion/request")
    suspend fun requestAccountDeletion(@Body body: AccountDeletionRequestBody): Response<AccountDeletionRequestResponse>

}
