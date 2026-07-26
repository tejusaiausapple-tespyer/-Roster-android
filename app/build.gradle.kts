import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android — AGP 9's built-in Kotlin support replaces it.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics.plugin)
    alias(libs.plugins.firebase.appdistribution.plugin)
}

// Release signing lives outside git in keystore.properties (see .gitignore) — back up
// keystore/rosterra-release.jks and this file somewhere durable. Losing either means
// staff can never receive an in-place update again; Android requires a matching signature.
val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.surainvestments.roster"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.surainvestments.roster"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.containsKey("storeFile")) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "WORKER_BASE_URL", "\"https://sura-roster.com/\"")
            firebaseAppDistribution {
                artifactType = "APK"
                releaseNotes = "Staff app build for internal testing."
                // Fill in before running appDistributionUploadRelease:
                // testers = "staff1@example.com, staff2@example.com"
                // groups = "staff-testers"
            }
        }
        debug {
            isMinifyEnabled = false
            // No applicationIdSuffix: the Firebase Android app is registered for the
            // bare com.surainvestments.roster only (one app in the console today).
            // Add a second Firebase app for "<applicationId>.debug" and merge its
            // client entry into google-services.json before reintroducing a debug
            // suffix for side-by-side install support.
            // Same shared backend as the other two clients by default. Override for a
            // local `wrangler dev` session with, e.g.:
            //   ./gradlew installDebug -PworkerBaseUrl=http://10.0.2.2:8787/
            // (10.0.2.2 is the Android emulator's alias for the host loopback interface.)
            val workerBaseUrl = (project.findProperty("workerBaseUrl") as String?) ?: "https://sura-roster.com/"
            buildConfigField("String", "WORKER_BASE_URL", "\"$workerBaseUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.play.services.location)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.crashlytics)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    // Regular (not debug-only) implementation: NetworkModule references this
    // class directly and gates it at runtime with `if (BuildConfig.DEBUG)` —
    // R8 dead-code-eliminates that branch in release, so nothing is added to
    // the release APK's behavior, but the class must be on every variant's
    // compile classpath.
    implementation(libs.okhttp.logging.interceptor)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
