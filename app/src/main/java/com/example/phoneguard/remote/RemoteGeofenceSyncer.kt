package com.example.phoneguard.remote

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.phoneguard.data.ChildSettingsStore
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

class RemoteGeofenceSyncer(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val backendClient = ChildBackendClient()
  private val geofencingClient = LocationServices.getGeofencingClient(appContext)

  fun sync(): Boolean {
    val identity = settingsStore.getOrCreatePairingIdentity()
    val deviceSecret = settingsStore.getOrCreateDeviceSecret()

    val zones =
      when (
        val result =
          backendClient.fetchGeofences(
            deviceId = identity.deviceId,
            deviceSecret = deviceSecret,
          )
      ) {
        is ChildGeofenceSyncResult.Success -> result.zones
        is ChildGeofenceSyncResult.Failure -> {
          Log.w(TAG, "Geofence fetch failed: " + result.message)
          return false
        }
      }

    if (zones.isNotEmpty() &&
      !ChildLocationAccess.hasRequiredLocationAccess(appContext)
    ) {
      settingsStore.setLocationAccessRequestedByParent(true)
      Log.w(TAG, "Geofence sync skipped because location access is incomplete")
      return false
    }

    return runCatching {
      Tasks.await(
        geofencingClient.removeGeofences(geofencePendingIntent()),
        TASK_TIMEOUT_SECONDS,
        TimeUnit.SECONDS,
      )

      if (zones.isEmpty()) {
        Log.i(TAG, "Geofence sync complete: no active zones")
        return@runCatching true
      }

      val geofences =
        zones.map { zone ->
          Geofence.Builder()
            .setRequestId(zone.zoneId)
            .setCircularRegion(
              zone.latitude,
              zone.longitude,
              zone.radiusMeters.toFloat(),
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
              Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT,
            )
            .build()
        }

      val request =
        GeofencingRequest.Builder()
          .setInitialTrigger(0)
          .addGeofences(geofences)
          .build()

      @Suppress("MissingPermission")
      Tasks.await(
        geofencingClient.addGeofences(
          request,
          geofencePendingIntent(),
        ),
        TASK_TIMEOUT_SECONDS,
        TimeUnit.SECONDS,
      )

      Log.i(TAG, "Geofence sync complete: " + zones.size + " zone(s)")
      true
    }.onFailure { error ->
      Log.e(TAG, "Geofence registration failed", error)
    }.getOrDefault(false)
  }

  private fun geofencePendingIntent(): PendingIntent {
    val intent =
      Intent(appContext, GeofenceBroadcastReceiver::class.java)

    val flags =
      PendingIntent.FLAG_UPDATE_CURRENT or
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          PendingIntent.FLAG_MUTABLE
        } else {
          0
        }

    return PendingIntent.getBroadcast(
      appContext,
      GEOFENCE_PENDING_INTENT_REQUEST_CODE,
      intent,
      flags,
    )
  }

  private companion object {
    const val TAG = "PhoneGuardGeofence"
    const val TASK_TIMEOUT_SECONDS = 20L
    const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 42017
  }
}
