plugins {
    alias(libs.plugins.android.application) apply false
    // No org.jetbrains.kotlin.android — AGP 9's built-in Kotlin support replaces it
    // (https://kotl.in/gradle/agp-built-in-kotlin). kotlin.plugin.compose/serialization
    // are separate Kotlin *compiler* plugins, still needed independently of this.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics.plugin) apply false
    alias(libs.plugins.firebase.appdistribution.plugin) apply false
}
