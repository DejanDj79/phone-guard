package com.example.phoneguard.parent.data

import android.content.Context
import android.content.SharedPreferences

class ParentAccountScopeStore(context: Context) {
  private val appContext = context.applicationContext
  private val accountPreferences =
    appContext.getSharedPreferences(ACCOUNT_PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun activeUserId(): String? =
    accountPreferences
      .getString(KEY_ACTIVE_USER_ID, null)
      ?.takeIf { it.isNotBlank() }

  fun activate(userId: String) {
    require(userId.isNotBlank()) { "Parent user id must not be blank." }

    migrateLegacyPairingsIfNeeded(userId)

    accountPreferences
      .edit()
      .putString(KEY_ACTIVE_USER_ID, userId)
      .apply()
  }

  fun clearActiveAccount() {
    accountPreferences
      .edit()
      .remove(KEY_ACTIVE_USER_ID)
      .apply()
  }

  fun settingsPreferencesName(): String {
    val userId = activeUserId() ?: return SIGNED_OUT_SETTINGS_NAME
    return scopedSettingsName(userId)
  }

  private fun migrateLegacyPairingsIfNeeded(userId: String) {
    if (accountPreferences.contains(KEY_LEGACY_MIGRATION_OWNER)) return

    val legacy =
      appContext.getSharedPreferences(
        LEGACY_SETTINGS_NAME,
        Context.MODE_PRIVATE,
      )
    val scoped =
      appContext.getSharedPreferences(
        scopedSettingsName(userId),
        Context.MODE_PRIVATE,
      )

    if (scoped.all.isEmpty() && legacy.all.isNotEmpty()) {
      scoped.edit().copyAllFrom(legacy).apply()
    }

    accountPreferences
      .edit()
      .putString(KEY_LEGACY_MIGRATION_OWNER, userId)
      .apply()
  }

  private fun scopedSettingsName(userId: String): String {
    val safeUserId =
      userId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    return SETTINGS_PREFIX + safeUserId
  }

  private companion object {
    const val ACCOUNT_PREFERENCES_NAME = "phone_guard_parent_account"
    const val KEY_ACTIVE_USER_ID = "active_user_id"
    const val KEY_LEGACY_MIGRATION_OWNER = "legacy_migration_owner"

    const val LEGACY_SETTINGS_NAME = "phone_guard_parent_settings"
    const val SETTINGS_PREFIX = "phone_guard_parent_settings_user_"
    const val SIGNED_OUT_SETTINGS_NAME = "phone_guard_parent_settings_signed_out"
  }
}

private fun SharedPreferences.Editor.copyAllFrom(
  source: SharedPreferences,
): SharedPreferences.Editor {
  source.all.forEach { (key, value) ->
    when (value) {
      is String -> putString(key, value)
      is Int -> putInt(key, value)
      is Long -> putLong(key, value)
      is Float -> putFloat(key, value)
      is Boolean -> putBoolean(key, value)
      is Set<*> -> {
        @Suppress("UNCHECKED_CAST")
        putStringSet(key, value as? Set<String>)
      }
    }
  }

  return this
}
