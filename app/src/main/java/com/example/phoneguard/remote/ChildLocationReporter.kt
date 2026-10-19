package com.example.phoneguard.remote

import android.content.Context
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
    settingsStore.setLocationAccessRequestedByParent(true)

    if (!ChildLocationAccess.hasRequiredLocationAccess(appContext)) {
      return false
    }

    val location = requestCurrentLocation() ?: return false
    val identity = settingsStore.getOrCreatePairingIdentity()

    val reported =
      when (
        backendClient.reportLocation(
          deviceId = identity.deviceId,
          deviceSecret = settingsStore.getOrCreateDeviceSecret(),
          latitude = location.latitude,
          longitude = location.longitude,
          accuracyMeters = location.accuracy.toDouble(),
          capturedAtMillis =
            location.time.takeIf { it > 0L }
              ?: System.currentTimeMillis(),
        )
      ) {
        ChildLocationReportResult.Success -> true
        is ChildLocationReportResult.Failure -> false
      }

    if (reported) {
      settingsStore.setLocationAccessRequestedByParent(false)
    }

    return reported
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
