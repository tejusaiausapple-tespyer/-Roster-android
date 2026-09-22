package com.surainvestments.roster.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Typed request/response models for every `/api` endpoint on the shared
 * Cloudflare Worker (`sura-roster.com`), verified directly against the
 * handlers under `Roster PWA/worker/handlers` and `worker/index.ts` (not
 * re-derived from the inventory docs) — see docs/ANDROID-BUILD-PLAN.md Phase 3.
 *
 * All responses are modelled with the success-path fields nullable where the
 * same shape can also carry an `error` — the Worker returns HTTP error status
 * codes (400/401/403/404/429/500) alongside an `{ "error": "..." }" body for
 * failures, so callers should check the HTTP status first and fall back to
 * [ApiErrorResponse] when it isn't 2xx, rather than relying on field presence
 * alone.
 */

@Serializable
data class ApiErrorResponse(
    val error: String? = null,
    val retryAfterSeconds: Int? = null,
)

@Serializable
data class HealthResponse(
    val ok: Boolean,
    val service: String,
    val missingBindings: List<String> = emptyList(),
)

/** Body-less endpoints (e.g. complete-password-change) still need a JSON body for OkHttp/Retrofit's POST. */
@Serializable
object EmptyRequestBody

@Serializable
data class OkResponse(val ok: Boolean = true)

// ─── Auth (worker/handlers/auth.ts) ───────────────────────────────────────────

// ─── Availability (worker/handlers/availability.ts) ───────────────────────────

@Serializable
data class SaveStaffAvailabilityRequest(
    val userId: String? = null,
    /** Raw passthrough — the exact weekly/day-level shape is nailed down in
     *  Phase 5's tolerant model layer, not guessed here in Phase 3. */
    val weeklyAvailability: JsonObject,
)

@Serializable
data class SaveStaffAvailabilityResponse(
    val ok: Boolean,
    val weeklyAvailability: JsonObject,
    val updatedAt: String,
)

// ─── Notifications (worker/handlers/notifications.ts) ─────────────────────────

@Serializable
data class SendNotificationRequest(
    val event: String,
    val shiftIds: List<String>? = null,
    val timesheetId: String? = null,
    val timesheetIds: List<String>? = null,
    val recipientIds: List<String>? = null,
)

/**
 * This endpoint is deliberately best-effort: `worker/index.ts` catches any
 * handler failure and still returns HTTP 200 with `ok: false, error: "..."`
 * rather than a 4xx/5xx, so a notification failure never surfaces as a hard
 * error to the caller.
 */
@Serializable
data class SendNotificationResponse(
    val ok: Boolean? = null,
    val skipped: Boolean? = null,
    val reason: String? = null,
    val error: String? = null,
    val event: String? = null,
    val recipients: Int? = null,
    val sent: Int? = null,
)

/**
 * `reason`: "login" claims this device as the account's single active
 * notification device (every other token doc for this uid is deactivated
 * server-side). "refresh" carries active status forward across a silent FCM
 * token rotation, and only does anything if `previousToken`'s doc was
 * already active — see worker/handlers/deviceActivation.ts.
 */
@Serializable
data class ActivateDeviceRequest(
    val token: String,
    val previousToken: String? = null,
    val reason: String,
)

/** Best-effort, same philosophy as SendNotificationResponse: worker/index.ts
 *  always returns HTTP 200 here even on internal failure (ok: false, error). */
@Serializable
data class ActivateDeviceResponse(
    val ok: Boolean? = null,
    val active: Boolean? = null,
    val error: String? = null,
    val skipped: Boolean? = null,
    val reason: String? = null,
)

// ─── Account deletion (worker/handlers/accountDeletion.ts) ────────────────────

@Serializable
data class AccountDeletionRequestBody(
    val staffUserId: String? = null,
    val via: String? = null, // "ios" | "pwa" | "support"
)

@Serializable
data class AccountDeletionRequestResponse(val ok: Boolean, val staffUserId: String, val status: String)
