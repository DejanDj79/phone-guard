package com.example.phoneguard.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.phoneguard.remote.ChildHeartbeatSender

class ProtectionStatusReceiver : BroadcastReceiver() {
  override fun onReceive(
    context: Context,
    intent: Intent?,
  ) {
    val pendingResult = goAsync()
    val appContext = context.applicationContext

    Thread {
      try {
        if (
          intent?.action ==
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        ) {
          ScheduleAlarmScheduler(appContext).syncCurrentStateAndScheduleNext()
        }

        ChildHeartbeatSender(appContext).send()
      } catch (error: Exception) {
        Log.w(TAG, "Protection status heartbeat failed", error)
      } finally {
        pendingResult.finish()
      }
    }.start()
  }

  private companion object {
    const val TAG = "PhoneGuardProtection"
  }
}
