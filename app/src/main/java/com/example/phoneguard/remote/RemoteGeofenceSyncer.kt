package com.example.phoneguard.remote

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.phoneguard.core.GeofenceZone
import com.example.phoneguard.data.ChildSettingsStore
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class RemoteGeofenceSyncer(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val backendClient = ChildBackendClient()
  private val geofencingClient = LocationServices.getGeofencingClient(appContext)
  private val preferences =
    appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun sync(force: Boolean = false): Boolean =
    synchronized(syncLock) {
      syncLocked(force)
    }

  private fun syncLocked(force: Boolean): Boolean {
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

    if (
      zones.isNotEmpty() &&
      !ChildLocationAccess.hasRequiredLocationAccess(appContext)
    ) {
      settingsStore.setLocationAccessRequestedByParent(true)
      Log.w(TAG, "Geofence sync skipped because location access is incomplete")
      return false
    }

    val fingerprint = zonesFingerprint(zones)
    val now = System.currentTimeMillis()
    val bootEpoch = now - SystemClock.elapsedRealtime()
    val lastSyncedAt = preferences.getLong(KEY_LAST_SYNCED_AT, 0L)
    val lastBootEpoch = preferences.getLong(KEY_BOOT_EPOCH, 0L)
    val lastPackageUpdate =
      preferences.getLong(KEY_PACKAGE_UPDATE_TIME, 0L)
    val packageUpdate =
      appContext.packageManager
        .getPackageInfo(appContext.packageName, 0)
        .lastUpdateTime

    val unchanged =
      fingerprint == preferences.getString(KEY_ZONES_FINGERPRINT, null)
    val registrationRecent =
      lastSyncedAt > 0L &&
        now >= lastSyncedAt &&
        now - lastSyncedAt < REGISTRATION_RECHECK_MILLIS
    val sameBoot =
      lastBootEpoch > 0L &&
        abs(bootEpoch - lastBootEpoch) < BOOT_EPOCH_TOLERANCE_MILLIS
    val sameInstallation =
      lastPackageUpdate > 0L &&
        lastPackageUpdate == packageUpdate

    // GMS keeps registrations across ordinary app restarts. Re-registering
    // unchanged zones on every Child sync discards the previous geofence state.
    // Always restore after reboot/app upgrade, and periodically as recovery.
    if (!force && unchanged && registrationRecent && sameBoot && sameInstallation) {
      Log.d(TAG, "Geofence sync skipped: " + zones.size + " unchanged zone(s)")
      return true
    }

    return runCatching {
      // Clear removed/disabled zones and replace changed ones as one refresh.
      Tasks.await(
        geofencingClient.removeGeofences(geofencePendingIntent()),
        TASK_TIMEOUT_SECONDS,
        TimeUnit.SECONDS,
      )

      if (zones.isNotEmpty()) {
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
      }

      // Only cache a successful refresh. If registration failed, retry the
      // next time the Child app syncs instead of treating it as registered.
      preferences.edit()
        .putString(KEY_ZONES_FINGERPRINT, fingerprint)
        .putLong(KEY_LAST_SYNCED_AT, now)
        .putLong(KEY_BOOT_EPOCH, bootEpoch)
        .putLong(KEY_PACKAGE_UPDATE_TIME, packageUpdate)
        .apply()

      Log.i(TAG, "Geofence registration complete: " + zones.size + " zone(s)")
      true
    }.onFailure { error ->
      preferences.edit()
        .remove(KEY_ZONES_FINGERPRINT)
        .remove(KEY_LAST_SYNCED_AT)
        .apply()
      Log.e(TAG, "Geofence registration failed", error)
    }.getOrDefault(false)
  }

  private fun zonesFingerprint(zones: List<GeofenceZone>): String {
    val canonical =
      zones.sortedBy { it.zoneId }
        .joinToString("\n") { zone ->
          listOf(
            zone.zoneId,
            zone.latitude.toString(),
            zone.longitude.toString(),
            zone.radiusMeters.toString(),
          ).joinToString(":")
        }
    val digest = MessageDigest.getInstance("SHA-256")
      .digest(canonical.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte ->
      (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
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
    const val PREFERENCES_NAME = "phone_guard_geofence_registration"
    const val KEY_ZONES_FINGERPRINT = "zones_fingerprint"
    const val KEY_LAST_SYNCED_AT = "last_synced_at"
    const val KEY_BOOT_EPOCH = "boot_epoch"
    const val KEY_PACKAGE_UPDATE_TIME = "package_update_time"
    const val REGISTRATION_RECHECK_MILLIS = 12 * 60 * 60 * 1000L
    const val BOOT_EPOCH_TOLERANCE_MILLIS = 30_000L
    val syncLock = Any()
  }
}
