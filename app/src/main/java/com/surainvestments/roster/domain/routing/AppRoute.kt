package com.surainvestments.roster.domain.routing

import com.surainvestments.roster.domain.model.AppUser
import com.surainvestments.roster.domain.model.UserRole

/**
 * The single screen the app should present, derived from session and profile
 * state. Mirrors iOS's `AppRoute.determine` (App/AppRoute.swift) exactly —
 * gate order matters (a prior iOS bug let managers bypass the forced
 * password-change and device-auth gates by getting this ordering wrong).
 *
 * Gate order (first match wins):
 *   restoring -> login -> profileLoading -> forcedPasswordChange ->
 *   profileCompletion -> deviceAuthGate -> managerMain / staffMain
 *
 * Unlike iOS there is no `setup` state: `google-services.json` is baked in at
 * build time for Android, so "Firebase not configured" isn't a runtime case
 * to gate on the way a missing `GoogleService-Info.plist` can be on iOS.
 *
 * - `forcedPasswordChange` and `deviceAuthGate` apply to BOTH roles.
 * - `profileCompletion` is effectively staff-only (`AppUser.needsProfileCompletion` is false for managers).
 */
enum class AppRoute {
    Restoring,
    Login,
    ProfileLoading,
    ForcedPasswordChange,
    ProfileCompletion,
    DeviceAuthGate,
    ManagerMain,
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
            if (user.mustChangePassword) return ForcedPasswordChange
            if (user.needsProfileCompletion) return ProfileCompletion
            if (deviceAuthEnabled && !deviceAuthVerified) return DeviceAuthGate
            return if (user.role == UserRole.Manager) ManagerMain else StaffMain
        }
    }
}
