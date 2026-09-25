package com.example.phoneguard.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.phoneguard.data.ChildSettingsStore
import java.util.Calendar

class ScheduleAlarmScheduler(context: Context) {
  private val appContext = context.applicationContext
  private val alarmManager =
    appContext.getSystemService(AlarmManager::class.java)
  private val settingsStore = ChildSettingsStore(appContext)

  fun syncCurrentStateAndScheduleNext() {
    settingsStore.setScheduleLock(settingsStore.shouldBeRestrictedNow())
    scheduleNext()
    syncTemporaryAllowanceAlarm()
  }

  fun scheduleNext() {
    cancelScheduleTransition()

    val transition =
      settingsStore
        .getWeeklySchedule()
        .nextTransitionAfter(Calendar.getInstance())
        ?: return

    scheduleAlarm(
      triggerAtMillis = transition.triggerAtMillis,
      operation = scheduleTransitionPendingIntent(),
    )
  }

  fun syncTemporaryAllowanceAlarm() {
    cancelTemporaryAllowanceExpiry()

    val until = settingsStore.temporaryAllowanceUntilMillis()
    val now = System.currentTimeMillis()

    if (until <= now) {
      if (until > 0L) {
        settingsStore.clearTemporaryAllowance()
      }
      return
    }

    scheduleAlarm(
      triggerAtMillis = until,
      operation = temporaryAllowancePendingIntent(),
    )
  }

  fun hasExactAlarmAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
      alarmManager.canScheduleExactAlarms()

  fun exactAlarmPermissionIntent(): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null

    return Intent(
      Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
      Uri.parse("package:" + appContext.packageName),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }

  fun scheduleProtectionStatusCheck(delayMillis: Long = 3_000L) {
    scheduleInexactProtectionStatusCheck(
      delayMillis = delayMillis,
      requestCode = REQUEST_CODE_PROTECTION_STATUS,
    )
  }

  fun scheduleProtectionStatusWatch() {
    listOf(
      2_000L to REQUEST_CODE_PROTECTION_STATUS,
      8_000L to REQUEST_CODE_PROTECTION_STATUS_SECOND,
      20_000L to REQUEST_CODE_PROTECTION_STATUS_THIRD,
    ).forEach { (delayMillis, requestCode) ->
      scheduleInexactProtectionStatusCheck(
        delayMillis = delayMillis,
        requestCode = requestCode,
      )
    }
  }

  private fun scheduleInexactProtectionStatusCheck(
    delayMillis: Long,
    requestCode: Int,
  ) {
    val triggerAtMillis =
      System.currentTimeMillis() + delayMillis.coerceAtLeast(1_000L)

    alarmManager.setAndAllowWhileIdle(
      AlarmManager.RTC_WAKEUP,
      triggerAtMillis,
      protectionStatusPendingIntent(requestCode),
    )
  }

  private fun scheduleAlarm(
    triggerAtMillis: Long,
    operation: PendingIntent,
  ) {
    if (hasExactAlarmAccess()) {
      alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        operation,
      )
    } else {
      alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        operation,
      )
    }
  }

  private fun cancelScheduleTransition() {
    alarmManager.cancel(scheduleTransitionPendingIntent())
  }

  private fun cancelTemporaryAllowanceExpiry() {
    alarmManager.cancel(temporaryAllowancePendingIntent())
  }

  private fun scheduleTransitionPendingIntent(): PendingIntent =
    PendingIntent.getBroadcast(
      appContext,
      REQUEST_CODE_SCHEDULE_TRANSITION,
      Intent(appContext, ScheduleAlarmReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun temporaryAllowancePendingIntent(): PendingIntent =
    PendingIntent.getBroadcast(
      appContext,
      REQUEST_CODE_TEMPORARY_ALLOWANCE,
      Intent(appContext, TemporaryAllowanceReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun protectionStatusPendingIntent(
    requestCode: Int = REQUEST_CODE_PROTECTION_STATUS,
  ): PendingIntent =
    PendingIntent.getBroadcast(
      appContext,
      requestCode,
      Intent(appContext, ProtectionStatusReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private companion object {
    const val REQUEST_CODE_SCHEDULE_TRANSITION = 4101
    const val REQUEST_CODE_TEMPORARY_ALLOWANCE = 4102
    const val REQUEST_CODE_PROTECTION_STATUS = 4103
    const val REQUEST_CODE_PROTECTION_STATUS_SECOND = 4104
    const val REQUEST_CODE_PROTECTION_STATUS_THIRD = 4105
  }
}
