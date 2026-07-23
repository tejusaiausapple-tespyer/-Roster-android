package com.surainvestments.roster.domain.model

/** Mirrors iOS's `AuthError` (Services/AuthService.swift) — same user-facing copy. Throwable, like Swift's `Error`. */
sealed class AuthError(override val message: String) : Exception(message) {
    object ProfileNotFound : AuthError("User profile not found. Contact your manager.")
    object AccountLocked : AuthError("Your account has been locked. Contact your manager.")
    object AccountInactive : AuthError("Your account is inactive. Contact your manager.")
    object NotAuthenticated : AuthError("You are not signed in.")
    object WrongPassword : AuthError("Your current password is incorrect.")
    object WeakPassword : AuthError("That password does not meet the requirements.")
    class Generic(message: String) : AuthError(message)
}
