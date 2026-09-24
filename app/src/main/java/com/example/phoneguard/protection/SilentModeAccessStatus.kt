package com.example.phoneguard.protection

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

object SilentModeAccessStatus {
  fun isGranted(context: Context): Boolean {
    val notificationManager =
      context.getSystemService(NotificationManager::class.java)
    return notificationManager.isNotificationPolicyAccessGranted
  }

  fun settingsIntent(): Intent =
    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
