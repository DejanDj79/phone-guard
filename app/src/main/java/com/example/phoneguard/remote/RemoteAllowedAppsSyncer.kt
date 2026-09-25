package com.example.phoneguard.remote

import android.content.Context
import android.util.Log
import com.example.phoneguard.data.ChildSettingsStore

class RemoteAllowedAppsSyncer(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val backendClient = ChildBackendClient()

  fun sync(): Boolean {
    val identity = settingsStore.getOrCreatePairingIdentity()

    return when (
      val result =
        backendClient.fetchAllowedApps(
          deviceId = identity.deviceId,
          deviceSecret = settingsStore.getOrCreateDeviceSecret(),
        )
    ) {
      is ChildAllowedAppsResult.Success -> {
        val localVersion = settingsStore.remoteAllowedAppsVersion()

        if (result.version <= localVersion) {
          Log.i(
            TAG,
            "Allowed apps already current: local=" +
              localVersion +
              ", remote=" +
              result.version,
          )
          true
        } else {
          val stored =
            settingsStore.saveRemoteAllowedApps(
              packages = result.packages,
              version = result.version,
            )

          if (stored) {
            Log.i(TAG, "Allowed apps applied: version=" + result.version)
          } else {
            Log.e(TAG, "Failed to persist allowed apps")
          }

          stored
        }
      }

      is ChildAllowedAppsResult.Failure -> {
        Log.w(TAG, "Allowed apps sync failed: " + result.message)
        false
      }
    }
  }

  private companion object {
    const val TAG = "PhoneGuardAllowedAppsSync"
  }
}
