package com.example.phoneguard.remote

import android.content.Context
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.core.RemoteCommandType
import com.example.phoneguard.data.ChildSettingsStore
import com.example.phoneguard.schedule.ScheduleAlarmScheduler

class RemoteCommandProcessor(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val alarmScheduler = ScheduleAlarmScheduler(appContext)

  fun apply(command: RemoteCommand) {
    when (command.type) {
      RemoteCommandType.LOCK -> {
        settingsStore.clearTemporaryAllowance()
        settingsStore.setManualLock(true)
        alarmScheduler.syncTemporaryAllowanceAlarm()
      }

      RemoteCommandType.UNLOCK -> {
        settingsStore.clearAllLocks()
        alarmScheduler.syncTemporaryAllowanceAlarm()
        alarmScheduler.scheduleNext()
      }

      RemoteCommandType.BONUS_TIME -> {
        val minutes = requireNotNull(command.bonusMinutes)
        settingsStore.grantTemporaryAllowance(minutes)
        alarmScheduler.syncTemporaryAllowanceAlarm()
      }

      RemoteCommandType.SYNC_SCHEDULE -> {
        check(RemoteScheduleSyncer(appContext).sync()) {
          "Remote schedule sync failed."
        }
      }

      RemoteCommandType.SYNC_ALLOWED_APPS -> {
        check(RemoteAllowedAppsSyncer(appContext).sync()) {
          "Remote allowed apps sync failed."
        }
      }
    }
  }
}
