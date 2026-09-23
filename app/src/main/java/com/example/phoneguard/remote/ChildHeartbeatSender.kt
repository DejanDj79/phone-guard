package com.example.phoneguard.remote

import android.content.Context
import android.util.Log
import com.example.phoneguard.data.ChildSettingsStore

class ChildHeartbeatSender(context: Context) {
  private val settingsStore = ChildSettingsStore(context.applicationContext)

  fun send() {
    val identity = settingsStore.getOrCreatePairingIdentity()
    val now = System.currentTimeMillis()
    val temporaryAllowanceUntil = settingsStore.temporaryAllowanceUntilMillis()
    val temporaryAllowanceActive = temporaryAllowanceUntil > now
    val accessState =
      when {
        temporaryAllowanceActive -> "TEMPORARILY_ALLOWED"
        settingsStore.isEffectivelyLocked(now) -> "LOCKED"
        else -> "ALLOWED"
      }

    when (
      val result =
        ChildBackendClient().heartbeat(
          deviceId = identity.deviceId,
          deviceSecret = settingsStore.getOrCreateDeviceSecret(),
          accessState = accessState,
          temporaryAllowUntilMillis =
            temporaryAllowanceUntil.takeIf { temporaryAllowanceActive },
        )
    ) {
      ChildHeartbeatResult.Success ->
        Log.i(TAG, "Heartbeat accepted")

      is ChildHeartbeatResult.Failure ->
        Log.w(TAG, "Heartbeat failed: " + result.message)
    }
  }

  private companion object {
    const val TAG = "PhoneGuardHeartbeat"
  }
}
