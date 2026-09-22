package com.example.phoneguard.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    ScheduleAlarmScheduler(context).syncCurrentStateAndScheduleNext()
  }
}

class ScheduleRestoreReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    ScheduleAlarmScheduler(context).syncCurrentStateAndScheduleNext()
  }
}
