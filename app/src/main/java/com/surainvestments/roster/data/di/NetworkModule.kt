package com.surainvestments.roster.data.di

import com.google.firebase.auth.FirebaseAuth
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.surainvestments.roster.BuildConfig
import com.surainvestments.roster.data.remote.FirebaseAuthInterceptor
import com.surainvestments.roster.data.remote.WorkerApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.time.Duration
import javax.inject.Singleton

/**
 * Retrofit/OkHttp wiring for the shared Cloudflare Worker API
 * (docs/ANDROID-BUILD-PLAN.md Phase 3) — the Android analogue of iOS's
 * `WorkerAPIClient.swift`.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideFirebaseAuthInterceptor(firebaseAuth: FirebaseAuth): FirebaseAuthInterceptor =
        FirebaseAuthInterceptor(firebaseAuth)

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: FirebaseAuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            // OkHttp's default is 10s each for connect/read/write — too tight for a staff member
            // on a weak cellular signal: confirmed live, a slow connection surfaced as a raw
            // SocketTimeoutException (message: "timeout") shown verbatim on the Availability save
            // banner. 30s gives real mobile latency room without letting a genuinely dead
            // connection hang indefinitely; see also `friendlyMessage` for how the resulting
            // exception (if it still happens) gets turned into readable text.
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.WORKER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideWorkerApiService(retrofit: Retrofit): WorkerApiService =
        retrofit.create(WorkerApiService::class.java)
}
