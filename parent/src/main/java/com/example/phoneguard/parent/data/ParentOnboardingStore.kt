package com.example.phoneguard.parent.data

import android.content.Context

class ParentOnboardingStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(
      PREFERENCES_NAME,
      Context.MODE_PRIVATE,
    )

  fun notificationStepCompleted(): Boolean =
    preferences.getBoolean(KEY_NOTIFICATION_STEP_COMPLETED, false)

  fun completeNotificationStep() {
    preferences
      .edit()
      .putBoolean(KEY_NOTIFICATION_STEP_COMPLETED, true)
      .apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "phone_guard_parent_onboarding"
    const val KEY_NOTIFICATION_STEP_COMPLETED = "notification_step_completed"
  }
}
