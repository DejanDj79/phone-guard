package com.example.phoneguard.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.phoneguard.data.ChildSettingsStore

class ScheduleAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    ScheduleAlarmScheduler(context).syncCurrentStateAndScheduleNext()
  }
}

class TemporaryAllowanceReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val settingsStore = ChildSettingsStore(context.applicationContext)
    settingsStore.clearTemporaryAllowance()
    ScheduleAlarmScheduler(context).syncCurrentStateAndScheduleNext()
  }
}

class ScheduleRestoreReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    ScheduleAlarmScheduler(context).syncCurrentStateAndScheduleNext()
  }
}
