package com.surainvestments.roster.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Typed failures so the clock-in flow can tell "denied" apart from "no fix yet". Mirrors iOS `LocationService.LocationError`. */
sealed class LocationError(message: String) : Exception(message) {
    object Denied : LocationError("Location access is off. Enable it in Settings so your shift location can be verified.")
    object Unavailable : LocationError("Couldn't get a GPS fix. Move to an open area and try again.")
}

/**
 * One-shot GPS capture for shift start/end verification. Assumes the runtime permission has
 * already been granted — requesting it is a UI-layer concern (`rememberLauncherForActivityResult`
 * in [com.surainvestments.roster.ui.staff.home.ClockInCard]), since only an Activity can show the
 * system prompt. Mirrors iOS `LocationService` minus the permission-prompt step.
 */
@Singleton
class LocationService @Inject constructor(@ApplicationContext private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Current fix. A cached fix under 30s old is reused to avoid the multi-second cold-start wait. */
    suspend fun currentLocation(): Location {
        if (!hasPermission()) throw LocationError.Denied
        try {
            val cached = client.lastLocation.await()
            if (cached != null && System.currentTimeMillis() - cached.time < 30_000L) {
                return cached
            }
            val cancellation = CancellationTokenSource()
            return client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
                ?: throw LocationError.Unavailable
        } catch (e: SecurityException) {
            throw LocationError.Denied
        }
    }
}
