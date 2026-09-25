package com.example.phoneguard.remote

import android.content.Context
import android.util.Log
import com.example.phoneguard.data.ChildSettingsStore

class RemoteDailyLimitSyncer(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val backendClient = ChildBackendClient()

  fun sync(): Boolean {
    val identity = settingsStore.getOrCreatePairingIdentity()

    return when (
      val result =
        backendClient.fetchDailyLimit(
          deviceId = identity.deviceId,
          deviceSecret = settingsStore.getOrCreateDeviceSecret(),
        )
    ) {
      is ChildDailyLimitResult.Success -> {
        val localVersion = settingsStore.remoteDailyLimitVersion()

        if (result.version <= localVersion) {
          Log.i(
            TAG,
            "Daily limit already current: local=" +
              localVersion +
              ", remote=" +
              result.version,
          )
          true
        } else if (
          !settingsStore.saveRemoteDailyLimit(
            minutes = result.minutes,
            version = result.version,
          )
        ) {
          Log.e(TAG, "Failed to persist remote daily limit")
          false
        } else {
          ChildHeartbeatSender(appContext).send()
          Log.i(
            TAG,
            "Remote daily limit applied: version=" +
              result.version +
              ", minutes=" +
              (result.minutes?.toString() ?: "disabled"),
          )
          true
        }
      }

      is ChildDailyLimitResult.Failure -> {
        Log.w(TAG, "Daily limit sync failed: " + result.message)
        false
      }
    }
  }

  private companion object {
    const val TAG = "PhoneGuardDailyLimitSync"
  }
}
