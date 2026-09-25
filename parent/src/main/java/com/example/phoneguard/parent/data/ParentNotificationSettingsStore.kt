package com.example.phoneguard.parent.data

import android.content.Context

class ParentNotificationSettingsStore(context: Context) {
  private val preferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun timeRequestNotificationsEnabled(): Boolean =
    preferences.getBoolean(KEY_TIME_REQUESTS_ENABLED, true)

  fun setTimeRequestNotificationsEnabled(enabled: Boolean) {
    preferences
      .edit()
      .putBoolean(KEY_TIME_REQUESTS_ENABLED, enabled)
      .apply()
  }

  fun protectionAlertNotificationsEnabled(): Boolean =
    preferences.getBoolean(KEY_PROTECTION_ALERTS_ENABLED, true)

  fun setProtectionAlertNotificationsEnabled(enabled: Boolean) {
    preferences
      .edit()
      .putBoolean(KEY_PROTECTION_ALERTS_ENABLED, enabled)
      .apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "phone_guard_parent_notification_settings"
    const val KEY_TIME_REQUESTS_ENABLED = "time_requests_enabled"
    const val KEY_PROTECTION_ALERTS_ENABLED = "protection_alerts_enabled"
  }
}
