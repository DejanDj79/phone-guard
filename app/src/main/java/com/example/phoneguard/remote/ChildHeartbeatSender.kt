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

  fun send(
    accessibilityEnabledOverride: Boolean? = null,
    protectionEvent: String? = null,
  ) {
    val identity = settingsStore.getOrCreatePairingIdentity()
    val now = System.currentTimeMillis()
    val temporaryAllowanceUntil = settingsStore.temporaryAllowanceUntilMillis()
    val dailyUsageDate = settingsStore.currentLocalDateKey(now)
    val dailyUsageSeconds = settingsStore.dailyUsageSecondsForToday(now)
    val appUsageMillis = settingsStore.appUsageMillisForToday(now)
    val appUsageTotalSeconds =
      (appUsageMillis.values.sum() / 1000L)
        .coerceIn(0L, 172800L)
        .toInt()
    val recentSessions =
      settingsStore.recentAppSessionsForToday(now)
    val recentSessionsByPackage =
      recentSessions.groupBy { it.packageName }

    val recentPackages =
      recentSessions
        .sortedByDescending { it.startedAtMillis }
        .map { it.packageName }

    val usagePackages =
      appUsageMillis.entries
        .asSequence()
        .filter { it.value >= 1000L }
        .sortedByDescending { it.value }
        .map { it.key }
        .toList()

    val appUsageEntries =
      (recentPackages + usagePackages)
        .distinct()
        .take(MAX_APP_USAGE_ENTRIES)
        .map { packageName ->
          val millis = appUsageMillis[packageName] ?: 0L
          val label =
            runCatching {
              val appInfo =
                appContext.packageManager.getApplicationInfo(packageName, 0)
              appContext.packageManager
                .getApplicationLabel(appInfo)
                .toString()
                .trim()
            }
              .getOrDefault("")
              .ifBlank { packageName.substringAfterLast('.') }

          val sessions =
            recentSessionsByPackage[packageName]
              .orEmpty()
              .sortedByDescending { it.startedAtMillis }
              .take(MAX_RECENT_SESSIONS_PER_APP)
              .map { session ->
                ChildAppUsageSession(
                  startedAtMillis = session.startedAtMillis,
                  endedAtMillis = session.endedAtMillis,
                  seconds =
                    (session.durationMillis / 1000L)
                      .coerceIn(0L, 86400L)
                      .toInt(),
                )
              }

          ChildAppUsageEntry(
            packageName = packageName,
            label = label.take(MAX_APP_LABEL_LENGTH),
            seconds =
              (millis / 1000L)
                .coerceIn(0L, 172800L)
                .toInt(),
            sessions = sessions,
          )
        }
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
    val preciseTimingEnabled = alarmScheduler.hasPreciseTimingPermission()
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
          protectionEvent = protectionEvent,
          dailyUsageDate = dailyUsageDate,
          dailyUsageSeconds = dailyUsageSeconds,
          appUsageDate = dailyUsageDate,
          appUsageTotalSeconds = appUsageTotalSeconds,
          appUsageEntries = appUsageEntries,
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
    const val MAX_APP_USAGE_ENTRIES = 30
    const val MAX_RECENT_SESSIONS_PER_APP = 20
    const val MAX_APP_LABEL_LENGTH = 120
  }
}
