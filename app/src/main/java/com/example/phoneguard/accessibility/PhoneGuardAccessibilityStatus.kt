package com.example.phoneguard.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object PhoneGuardAccessibilityStatus {
  fun isEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java)

    val enabledByManager =
      manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
          val serviceInfo = info.resolveInfo.serviceInfo
          serviceInfo.packageName == context.packageName &&
            serviceInfo.name == PhoneGuardAccessibilityService::class.java.name
        }

    if (enabledByManager) return true

    val expectedComponent =
      ComponentName(
        context,
        PhoneGuardAccessibilityService::class.java,
      )

    val enabledServices =
      Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ).orEmpty()

    return enabledServices
      .split(':')
      .mapNotNull(ComponentName::unflattenFromString)
      .any { component ->
        component.packageName == expectedComponent.packageName &&
          component.className == expectedComponent.className
      }
  }

  fun settingsIntent(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
