package com.example.phoneguard.parent.data

import android.content.Context
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState

class ParentSettingsStore(context: Context) {
  private val preferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun savePairing(
    device: ChildDevice,
    controlToken: String,
  ) {
    require(controlToken.isNotBlank()) { "controlToken must not be blank." }

    preferences
      .edit()
      .putString(KEY_DEVICE_ID, device.deviceId)
      .putString(KEY_DISPLAY_NAME, device.displayName)
      .putString(KEY_DEVICE_STATE, device.state.name)
      .putInt(
        KEY_TEMPORARY_MINUTES,
        device.temporaryAccessMinutesRemaining ?: 0,
      )
      .putString(KEY_CONTROL_TOKEN, controlToken)
      .apply()
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
        preferences.getInt(KEY_TEMPORARY_MINUTES, 0).takeIf { it > 0 }
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
      )
    }.getOrNull()
  }

  fun controlToken(): String? =
    preferences.getString(KEY_CONTROL_TOKEN, null)

  fun clearPairing() {
    preferences.edit().clear().apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "phone_guard_parent_settings"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_DISPLAY_NAME = "display_name"
    const val KEY_DEVICE_STATE = "device_state"
    const val KEY_TEMPORARY_MINUTES = "temporary_minutes"
    const val KEY_CONTROL_TOKEN = "control_token"
  }
}
