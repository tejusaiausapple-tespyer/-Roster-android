package com.surainvestments.roster.domain.model

/** Thrown for any failed `/api` call outside the auth flow (which uses [AuthError] instead). */
class WorkerApiException(message: String) : Exception(message)
