package com.example.phoneguard.protection

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

object BackgroundProtectionStatus {
  fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java)
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
  }

  fun batteryOptimizationSettingsIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
