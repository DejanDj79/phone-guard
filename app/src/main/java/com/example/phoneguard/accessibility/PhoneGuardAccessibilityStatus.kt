package com.example.phoneguard.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object PhoneGuardAccessibilityStatus {
  fun isEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java)

    return manager
      .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
      .any { info ->
        val serviceInfo = info.resolveInfo.serviceInfo
        serviceInfo.packageName == context.packageName &&
          serviceInfo.name == PhoneGuardAccessibilityService::class.java.name
      }
  }

  fun settingsIntent(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
