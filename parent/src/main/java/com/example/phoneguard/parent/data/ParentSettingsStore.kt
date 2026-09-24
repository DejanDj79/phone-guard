package com.example.phoneguard.parent.data

import android.content.Context
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.DeviceProtectionStatus

class ParentSettingsStore(context: Context) {
  private val preferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val tokenCipher = ParentTokenCipher()

  fun savePairing(
    device: ChildDevice,
    controlToken: String,
  ) {
    require(controlToken.isNotBlank()) { "controlToken must not be blank." }

    val editor =
      preferences
        .edit()
        .putString(KEY_DEVICE_ID, device.deviceId)
        .putString(KEY_DISPLAY_NAME, device.displayName)
        .putString(KEY_DEVICE_STATE, device.state.name)
        .putInt(
          KEY_TEMPORARY_MINUTES,
          device.temporaryAccessMinutesRemaining ?: 0,
        )
        .putString(KEY_LAST_SEEN_AT, device.lastSeenAt)
        .putBoolean(KEY_IS_ONLINE, device.isOnline)
        .putString(
          KEY_CONTROL_TOKEN_ENCRYPTED,
          tokenCipher.encrypt(controlToken),
        )
        .remove(KEY_CONTROL_TOKEN)

    device.protectionStatus.accessibilityEnabled?.let {
      editor.putBoolean(KEY_ACCESSIBILITY_ENABLED, it)
    }
    device.protectionStatus.preciseTimingEnabled?.let {
      editor.putBoolean(KEY_PRECISE_TIMING_ENABLED, it)
    }
    device.protectionStatus.batteryUnrestricted?.let {
      editor.putBoolean(KEY_BATTERY_UNRESTRICTED, it)
    }

    editor.apply()
  }

  fun loadPairedDevice(): ChildDevice? {
    val deviceId = preferences.getString(KEY_DEVICE_ID, null) ?: return null
    val displayName = preferences.getString(KEY_DISPLAY_NAME, null) ?: return null
    val stateName = preferences.getString(KEY_DEVICE_STATE, null) ?: return null
    val state =
      runCatching { DeviceAccessState.valueOf(stateName) }
        .getOrElse { return null }

    val temporaryMinutes =
      if (state == DeviceAccessState.TEMPORARILY_ALLOWED) {
        preferences.getInt(KEY_TEMPORARY_MINUTES, 0).takeIf { it >= 0 }
          ?: return null
      } else {
        null
      }

    return runCatching {
      ChildDevice(
        deviceId = deviceId,
        displayName = displayName,
        state = state,
        temporaryAccessMinutesRemaining = temporaryMinutes,
        isOnline =
          if (preferences.contains(KEY_IS_ONLINE)) {
            preferences.getBoolean(KEY_IS_ONLINE, false)
          } else {
            true
          },
        lastSeenAt = preferences.getString(KEY_LAST_SEEN_AT, null),
        protectionStatus =
          DeviceProtectionStatus(
            accessibilityEnabled =
              preferences.nullableBoolean(KEY_ACCESSIBILITY_ENABLED),
            preciseTimingEnabled =
              preferences.nullableBoolean(KEY_PRECISE_TIMING_ENABLED),
            batteryUnrestricted =
              preferences.nullableBoolean(KEY_BATTERY_UNRESTRICTED),
          ),
      )
    }.getOrNull()
  }

  fun controlToken(): String? {
    preferences.getString(KEY_CONTROL_TOKEN_ENCRYPTED, null)?.let { encrypted ->
      return tokenCipher.decrypt(encrypted)
    }

    val legacyToken =
      preferences.getString(KEY_CONTROL_TOKEN, null)
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return runCatching {
      val encrypted = tokenCipher.encrypt(legacyToken)
      preferences
        .edit()
        .putString(KEY_CONTROL_TOKEN_ENCRYPTED, encrypted)
        .remove(KEY_CONTROL_TOKEN)
        .commit()

      legacyToken
    }.getOrNull()
  }

  fun clearPairing() {
    preferences.edit().clear().apply()
    runCatching { tokenCipher.deleteKey() }
  }

  private companion object {
    const val PREFERENCES_NAME = "phone_guard_parent_settings"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_DISPLAY_NAME = "display_name"
    const val KEY_DEVICE_STATE = "device_state"
    const val KEY_TEMPORARY_MINUTES = "temporary_minutes"
    const val KEY_LAST_SEEN_AT = "last_seen_at"
    const val KEY_IS_ONLINE = "is_online"
    const val KEY_CONTROL_TOKEN = "control_token"
    const val KEY_CONTROL_TOKEN_ENCRYPTED = "control_token_encrypted"
    const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
    const val KEY_PRECISE_TIMING_ENABLED = "precise_timing_enabled"
    const val KEY_BATTERY_UNRESTRICTED = "battery_unrestricted"
  }
}


private fun android.content.SharedPreferences.nullableBoolean(
  key: String,
): Boolean? =
  if (contains(key)) getBoolean(key, false) else null
