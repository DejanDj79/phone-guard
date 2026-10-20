package com.example.phoneguard.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.phoneguard.data.ChildSettingsStore
import com.example.phoneguard.remote.ChildHeartbeatSender
import com.example.phoneguard.remote.RemoteGeofenceSyncer

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

    val pendingResult = goAsync()
    Thread {
      try {
        RemoteGeofenceSyncer(context.applicationContext).sync(force = true)
        ChildHeartbeatSender(context.applicationContext).send()
      } finally {
        pendingResult.finish()
      }
    }.start()
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
