package com.surainvestments.roster.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors iOS `@AppStorage("preferredColorScheme")` — system / light / dark. */
enum class AppearanceMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromStorage(value: String?): AppearanceMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

@Singleton
class AppearancePreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun getMode(): AppearanceMode = AppearanceMode.fromStorage(prefs.getString(KEY, null))

    fun setMode(mode: AppearanceMode) {
        prefs.edit { putString(KEY, mode.storageValue) }
    }

    private companion object {
        const val KEY = "preferred_color_scheme"
    }
}
