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
  }

  fun scheduleNext() {
    cancelCurrent()

    val transition =
      settingsStore
        .getWeeklySchedule()
        .nextTransitionAfter(Calendar.getInstance())
        ?: return

    val operation = alarmPendingIntent()

    if (hasExactAlarmAccess()) {
      alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        transition.triggerAtMillis,
        operation,
      )
    } else {
      alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        transition.triggerAtMillis,
        operation,
      )
    }
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

  private fun cancelCurrent() {
    alarmManager.cancel(alarmPendingIntent())
  }

  private fun alarmPendingIntent(): PendingIntent =
    PendingIntent.getBroadcast(
      appContext,
      REQUEST_CODE_SCHEDULE_TRANSITION,
      Intent(appContext, ScheduleAlarmReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private companion object {
    const val REQUEST_CODE_SCHEDULE_TRANSITION = 4101
  }
}
