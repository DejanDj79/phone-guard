package com.example.phoneguard.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.phoneguard.data.ChildSettingsStore
import com.example.phoneguard.remote.ChildHeartbeatSender

class ScheduleAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    ScheduleAlarmScheduler(context).syncCurrentStateAndScheduleNext()
    sendHeartbeatAsync(context)
  }
}

class TemporaryAllowanceReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val settingsStore = ChildSettingsStore(context.applicationContext)
    settingsStore.clearTemporaryAllowance()
    ScheduleAlarmScheduler(context).syncCurrentStateAndScheduleNext()
    sendHeartbeatAsync(context)
  }
}

class ScheduleRestoreReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    ScheduleAlarmScheduler(context).syncCurrentStateAndScheduleNext()
    sendHeartbeatAsync(context)
  }
}

private fun BroadcastReceiver.sendHeartbeatAsync(context: Context) {
  val pendingResult = goAsync()

  Thread {
    try {
      ChildHeartbeatSender(context.applicationContext).send()
    } finally {
      pendingResult.finish()
    }
  }.start()
}
