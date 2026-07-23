package com.surainvestments.roster.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches `Authorization: Bearer <Firebase ID token>` to every Worker API
 * call, mirroring iOS's `WorkerAPIClient.swift` bearer-token pattern (see
 * docs/ANDROID-BUILD-PLAN.md §2). OkHttp interceptors run on the call's own
 * dispatcher thread (never the caller's), so the blocking [Tasks.await] here
 * is safe — it must never be invoked from the main thread directly.
 */
class FirebaseAuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val user = firebaseAuth.currentUser ?: return chain.proceed(original)

        val idToken = runCatching { Tasks.await(user.getIdToken(false)).token }.getOrNull()
            ?: return chain.proceed(original)

        val authorized = original.newBuilder()
            .header("Authorization", "Bearer $idToken")
            .build()
        return chain.proceed(authorized)
    }
}
