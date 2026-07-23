package com.surainvestments.roster.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Process-lifetime scope for repository-level shared listeners (e.g. [ApplicationScope]-scoped
 * `stateIn` caches) — so a Firestore listener started by one ViewModel stays alive and shared
 * across every other consumer collecting the same [kotlinx.coroutines.flow.StateFlow], rather
 * than each ViewModel opening its own duplicate listener on the same query.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
