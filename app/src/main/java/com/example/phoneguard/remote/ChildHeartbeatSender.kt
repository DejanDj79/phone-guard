package com.example.phoneguard.remote

import android.content.Context
import android.util.Log
import com.example.phoneguard.accessibility.PhoneGuardAccessibilityStatus
import com.example.phoneguard.data.ChildSettingsStore
import com.example.phoneguard.protection.BackgroundProtectionStatus
import com.example.phoneguard.schedule.ScheduleAlarmScheduler

class ChildHeartbeatSender(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val alarmScheduler = ScheduleAlarmScheduler(appContext)

  fun send(accessibilityEnabledOverride: Boolean? = null) {
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

    val accessibilityEnabled =
      accessibilityEnabledOverride
        ?: PhoneGuardAccessibilityStatus.isEnabled(appContext)
    val preciseTimingEnabled = alarmScheduler.hasExactAlarmAccess()
    val batteryUnrestricted =
      BackgroundProtectionStatus.isBatteryOptimizationIgnored(appContext)

    when (
      val result =
        ChildBackendClient().heartbeat(
          deviceId = identity.deviceId,
          deviceSecret = settingsStore.getOrCreateDeviceSecret(),
          accessState = accessState,
          temporaryAllowUntilMillis =
            temporaryAllowanceUntil.takeIf { temporaryAllowanceActive },
          accessibilityEnabled = accessibilityEnabled,
          preciseTimingEnabled = preciseTimingEnabled,
          batteryUnrestricted = batteryUnrestricted,
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
