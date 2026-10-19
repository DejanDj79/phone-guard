package com.example.phoneguard.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.phoneguard.data.ChildSettingsStore
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ChildLocationReporter(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val fusedLocationClient =
    LocationServices.getFusedLocationProviderClient(appContext)
  private val backendClient = ChildBackendClient()

  fun reportCurrentLocation(): Boolean {
    if (!hasLocationPermission()) return false

    val location = requestCurrentLocation() ?: return false
    val identity = settingsStore.getOrCreatePairingIdentity()

    return when (
      backendClient.reportLocation(
        deviceId = identity.deviceId,
        deviceSecret = settingsStore.getOrCreateDeviceSecret(),
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyMeters = location.accuracy.toDouble(),
        capturedAtMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
      )
    ) {
      ChildLocationReportResult.Success -> true
      is ChildLocationReportResult.Failure -> false
    }
  }

  private fun hasLocationPermission(): Boolean {
    val fineGranted =
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.ACCESS_FINE_LOCATION,
      ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted =
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.ACCESS_COARSE_LOCATION,
      ) == PackageManager.PERMISSION_GRANTED

    if (!fineGranted && !coarseGranted) return false

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  @Suppress("MissingPermission")
  private fun requestCurrentLocation(): android.location.Location? {
    val latch = CountDownLatch(1)
    val cancellationTokenSource = CancellationTokenSource()
    var result: android.location.Location? = null

    fusedLocationClient
      .getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        cancellationTokenSource.token,
      )
      .addOnSuccessListener { location ->
        result = location
        latch.countDown()
      }
      .addOnFailureListener {
        latch.countDown()
      }
      .addOnCanceledListener {
        latch.countDown()
      }

    val completed =
      runCatching {
        latch.await(LOCATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      }.getOrDefault(false)

    if (!completed) {
      cancellationTokenSource.cancel()
      return null
    }

    return result
  }

  private companion object {
    const val LOCATION_TIMEOUT_SECONDS = 20L
  }
}
