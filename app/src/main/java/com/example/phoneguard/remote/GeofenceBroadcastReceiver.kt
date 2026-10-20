package com.example.phoneguard.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.phoneguard.core.GeofenceTransitionType
import com.example.phoneguard.data.ChildSettingsStore
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    if (intent == null) return

    val event = GeofencingEvent.fromIntent(intent) ?: return
    if (event.hasError()) {
      Log.e(TAG, "Geofence event error code=" + event.errorCode)
      return
    }

    val transition =
      when (event.geofenceTransition) {
        Geofence.GEOFENCE_TRANSITION_ENTER ->
          GeofenceTransitionType.ENTER
        Geofence.GEOFENCE_TRANSITION_EXIT ->
          GeofenceTransitionType.EXIT
        else -> {
          Log.w(TAG, "Unsupported geofence transition=" + event.geofenceTransition)
          return
        }
      }

    val triggeringGeofences = event.triggeringGeofences.orEmpty()
    if (triggeringGeofences.isEmpty()) return

    val pendingResult = goAsync()

    Thread {
      try {
        val appContext = context.applicationContext
        val settingsStore = ChildSettingsStore(appContext)
        val identity = settingsStore.getOrCreatePairingIdentity()
        val deviceSecret = settingsStore.getOrCreateDeviceSecret()
        val backendClient = ChildBackendClient()
        val occurredAtMillis = System.currentTimeMillis()

        triggeringGeofences.forEach { geofence ->
          when (
            val result =
              backendClient.reportGeofenceEvent(
                deviceId = identity.deviceId,
                deviceSecret = deviceSecret,
                zoneId = geofence.requestId,
                eventType = transition.name,
                occurredAtMillis = occurredAtMillis,
              )
          ) {
            ChildGeofenceEventReportResult.Success ->
              Log.i(
                TAG,
                "Reported " + transition.name + " for zone " + geofence.requestId,
              )

            is ChildGeofenceEventReportResult.Failure ->
              Log.w(
                TAG,
                "Geofence event report failed: " + result.message,
              )
          }
        }
      } finally {
        pendingResult.finish()
      }
    }.start()
  }

  private companion object {
    const val TAG = "PhoneGuardGeofence"
  }
}
