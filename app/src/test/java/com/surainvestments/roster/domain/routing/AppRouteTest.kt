package com.surainvestments.roster.domain.routing

import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.UserStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ported 1:1 from iOS's `AppRouteTests.swift` (RosterraTests) — same truth
 * table, same "Milestone 3" regression names, minus the `setup`/`hasConfig`
 * case which has no Android equivalent (see AppRoute.kt doc comment).
 */
class AppRouteTest {

    private val completeProfile = mapOf(
        "dob" to "1990-01-01",
        "address" to "1 Test St",
        "phone" to "0400000000",
    )

    private fun user(
        role: String = "staff",
        extra: Map<String, Any?> = emptyMap(),
    ): AppUser {
        val data = mutableMapOf<String, Any?>("role" to role)
        data.putAll(extra)
        return AppUser.fromDocument("uid-1", data)
    }

    private val staff get() = user(extra = completeProfile)
    private val manager get() = user(role = "manager")

    private fun route(
        restoring: Boolean = false,
        uid: String? = "uid-1",
        appUser: AppUser? = null,
        deviceAuthEnabled: Boolean = false,
        deviceAuthVerified: Boolean = false,
    ): AppRoute = AppRoute.determine(
        isRestoring = restoring,
        uid = uid,
        user = appUser,
        deviceAuthEnabled = deviceAuthEnabled,
        deviceAuthVerified = deviceAuthVerified,
    )

    // ── Pre-auth states ────────────────────────────────────────────────────

    @Test
    fun `restoring session shows restoring`() {
        assertEquals(AppRoute.Restoring, route(restoring = true))
    }

    @Test
    fun `no session shows login`() {
        assertEquals(AppRoute.Login, route(uid = null))
    }

    @Test
    fun `session without profile shows profile loading`() {
        assertEquals(AppRoute.ProfileLoading, route(appUser = null))
    }

    // ── Happy paths ─────────────────────────────────────────────────────────

    @Test
    fun `staff with complete profile goes to staff main`() {
        assertEquals(AppRoute.StaffMain, route(appUser = staff))
    }

    @Test
    fun `manager is rejected by staff-only routing`() {
        assertEquals(AppRoute.Login, route(appUser = manager))
    }

    // ── Forced password change (staff only) ─────────────────────────────────

    @Test
    fun `staff forced password change`() {
        val u = user(extra = completeProfile + mapOf("mustChangePassword" to true))
        assertEquals(AppRoute.ForcedPasswordChange, route(appUser = u))
    }

    @Test
    fun `manager cannot reach forced password change`() {
        val u = user(role = "manager", extra = mapOf("mustChangePassword" to true))
        assertEquals(
            "manager accounts must not enter any authenticated Android surface",
            AppRoute.Login,
            route(appUser = u),
        )
    }

    // ── Profile completion (staff only, by model) ────────────────────────────

    @Test
    fun `staff incomplete profile gated`() {
        assertEquals(AppRoute.ProfileCompletion, route(appUser = user()))
    }

    @Test
    fun `staff profile update required gated`() {
        val u = user(extra = completeProfile + mapOf("profileUpdateRequired" to true))
        assertEquals(AppRoute.ProfileCompletion, route(appUser = u))
    }

    @Test
    fun `manager cannot reach profile completion`() {
        assertEquals(
            "manager accounts are outside the Android product boundary",
            AppRoute.Login,
            route(appUser = user(role = "manager")),
        )
    }

    // ── Device auth gate (staff only) ────────────────────────────────────────

    @Test
    fun `staff device auth gate`() {
        assertEquals(AppRoute.DeviceAuthGate, route(appUser = staff, deviceAuthEnabled = true, deviceAuthVerified = false))
        assertEquals(AppRoute.StaffMain, route(appUser = staff, deviceAuthEnabled = true, deviceAuthVerified = true))
    }

    @Test
    fun `manager cannot reach device auth gate`() {
        assertEquals(
            "manager accounts must be rejected before device auth",
            AppRoute.Login,
            route(appUser = manager, deviceAuthEnabled = true, deviceAuthVerified = false),
        )
        assertEquals(AppRoute.Login, route(appUser = manager, deviceAuthEnabled = true, deviceAuthVerified = true))
    }

    // ── Gate precedence ───────────────────────────────────────────────────────

    @Test
    fun `password change beats profile completion and device auth`() {
        val u = user(extra = mapOf("mustChangePassword" to true)) // also incomplete profile
        assertEquals(AppRoute.ForcedPasswordChange, route(appUser = u, deviceAuthEnabled = true))
    }

    @Test
    fun `profile completion beats device auth`() {
        assertEquals(AppRoute.ProfileCompletion, route(appUser = user(), deviceAuthEnabled = true))
    }
}
