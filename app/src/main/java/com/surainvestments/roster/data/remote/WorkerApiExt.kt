package com.surainvestments.roster.data.remote

import com.surainvestments.roster.domain.model.WorkerApiException
import kotlinx.serialization.json.Json
import retrofit2.Response

private val errorJson = Json { ignoreUnknownKeys = true }

/** Unwraps a successful Worker response body, or throws [WorkerApiException] with the server's own error message. */
fun <T> Response<T>.bodyOrThrow(): T {
    if (isSuccessful) return body() ?: throw WorkerApiException("Empty response from server")
    val raw = errorBody()?.string()
    val message = raw?.let { runCatching { errorJson.decodeFromString<ApiErrorResponse>(it).error }.getOrNull() }
    throw WorkerApiException(message ?: "Request failed (HTTP ${code()})")
}
