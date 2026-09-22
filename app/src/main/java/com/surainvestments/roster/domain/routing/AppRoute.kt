package com.surainvestments.roster.domain.routing

import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.UserRole

/**
 * The single screen the app should present, derived from session and profile
 * state. Mirrors iOS's `AppRoute.determine` (App/AppRoute.swift) exactly —
 * This Android product is staff-only. A non-staff profile is never routed
 * into an authenticated surface; [AuthViewModel] signs it out immediately.
 *
 * Gate order (first match wins):
 *   restoring -> login -> profileLoading -> staff-role boundary ->
 *   forcedPasswordChange -> profileCompletion ->
 *   deviceAuthGate -> staffMain
 *
 * Unlike iOS there is no `setup` state: `google-services.json` is baked in at
 * build time for Android, so "Firebase not configured" isn't a runtime case
 * to gate on the way a missing `GoogleService-Info.plist` can be on iOS.
 *
 * The role boundary intentionally precedes every post-login gate so manager
 * credentials cannot reach password, profile, device-auth, or app content.
 */
enum class AppRoute {
    Restoring,
    Login,
    ProfileLoading,
    ForcedPasswordChange,
    ProfileCompletion,
    DeviceAuthGate,
    StaffMain,
    ;

    companion object {
        fun determine(
            isRestoring: Boolean,
            uid: String?,
            user: AppUser?,
            deviceAuthEnabled: Boolean,
            deviceAuthVerified: Boolean,
        ): AppRoute {
            if (isRestoring) return Restoring
            if (uid == null) return Login
            if (user == null) return ProfileLoading
            if (user.role != UserRole.Staff) return Login
            if (user.mustChangePassword) return ForcedPasswordChange
            if (user.needsProfileCompletion) return ProfileCompletion
            if (deviceAuthEnabled && !deviceAuthVerified) return DeviceAuthGate
            return StaffMain
        }
    }
}
