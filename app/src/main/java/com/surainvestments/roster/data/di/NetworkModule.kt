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
