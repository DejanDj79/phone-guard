package com.example.phoneguard.parent.data

import android.content.Context
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.DeviceProtectionStatus
import org.json.JSONArray
import org.json.JSONObject

data class PairedChildDevice(
  val device: ChildDevice,
  val controlToken: String,
)

class ParentSettingsStore(context: Context) {
  private val preferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val tokenCipher = ParentTokenCipher()

  /**
   * Saves or updates one paired Child without removing any other pairings.
   * The old single-device API remains valid while the Parent UI is migrated.
   */
  fun savePairing(
    device: ChildDevice,
    controlToken: String,
  ) {
    require(controlToken.isNotBlank()) { "controlToken must not be blank." }

    ensureMultiDeviceStorage()
    val pairings = loadPairingsInternal().toMutableList()
    val replacement = PairedChildDevice(device, controlToken)
    val index = pairings.indexOfFirst { it.device.deviceId == device.deviceId }

    if (index >= 0) {
      pairings[index] = replacement
    } else {
      pairings += replacement
    }

    savePairingsInternal(pairings)
    preferences.edit().putString(KEY_SELECTED_DEVICE_ID, device.deviceId).apply()
  }

  fun loadPairedDevices(): List<ChildDevice> {
    ensureMultiDeviceStorage()
    return loadPairingsInternal().map { it.device }
  }

  fun loadPairedDevice(): ChildDevice? {
    ensureMultiDeviceStorage()
    val pairings = loadPairingsInternal()
    if (pairings.isEmpty()) return null

    val selectedId = preferences.getString(KEY_SELECTED_DEVICE_ID, null)
    return pairings.firstOrNull { it.device.deviceId == selectedId }?.device
      ?: pairings.first().device.also {
        preferences.edit().putString(KEY_SELECTED_DEVICE_ID, it.deviceId).apply()
      }
  }

  fun selectDevice(deviceId: String): Boolean {
    ensureMultiDeviceStorage()
    if (loadPairingsInternal().none { it.device.deviceId == deviceId }) {
      return false
    }

    preferences.edit().putString(KEY_SELECTED_DEVICE_ID, deviceId).apply()
    return true
  }

  fun selectedDeviceId(): String? = loadPairedDevice()?.deviceId

  fun controlToken(): String? {
    val deviceId = selectedDeviceId() ?: return null
    return controlToken(deviceId)
  }

  fun controlToken(deviceId: String): String? {
    ensureMultiDeviceStorage()
    return loadPairingsInternal()
      .firstOrNull { it.device.deviceId == deviceId }
      ?.controlToken
  }

  fun removePairing(deviceId: String) {
    ensureMultiDeviceStorage()
    val remaining =
      loadPairingsInternal().filterNot { it.device.deviceId == deviceId }

    savePairingsInternal(remaining)

    val selectedId = preferences.getString(KEY_SELECTED_DEVICE_ID, null)
    if (selectedId == deviceId) {
      val editor = preferences.edit()
      val nextId = remaining.firstOrNull()?.device?.deviceId
      if (nextId == null) {
        editor.remove(KEY_SELECTED_DEVICE_ID)
      } else {
        editor.putString(KEY_SELECTED_DEVICE_ID, nextId)
      }
      editor.apply()
    }

    if (remaining.isEmpty()) {
      runCatching { tokenCipher.deleteKey() }
    }
  }

  /**
   * Legacy behavior used by the current single-device UI.
   * Once Device list UI is connected, unpair should call removePairing(deviceId).
   */
  fun clearPairing() {
    preferences.edit().clear().apply()
    runCatching { tokenCipher.deleteKey() }
  }

  private fun ensureMultiDeviceStorage() {
    if (preferences.contains(KEY_PAIRED_DEVICES)) return

    val legacyDevice = loadLegacyPairedDevice() ?: return
    val legacyToken = loadLegacyControlToken() ?: return

    savePairingsInternal(listOf(PairedChildDevice(legacyDevice, legacyToken)))
    preferences
      .edit()
      .putString(KEY_SELECTED_DEVICE_ID, legacyDevice.deviceId)
      .removeLegacyPairingKeys()
      .apply()
  }

  private fun loadPairingsInternal(): List<PairedChildDevice> {
    val raw = preferences.getString(KEY_PAIRED_DEVICES, null) ?: return emptyList()

    return runCatching {
      val array = JSONArray(raw)
      buildList {
        for (index in 0 until array.length()) {
          val json = array.getJSONObject(index)
          val state = DeviceAccessState.valueOf(json.getString("state"))
          val temporaryMinutes =
            if (state == DeviceAccessState.TEMPORARILY_ALLOWED) {
              json.optInt("temporaryMinutes", 0).coerceAtLeast(0)
            } else {
              null
            }

          val encryptedToken = json.getString("controlTokenEncrypted")
          val controlToken =
            tokenCipher.decrypt(encryptedToken)
              ?.takeIf { it.isNotBlank() }
              ?: continue

          add(
            PairedChildDevice(
              device =
                ChildDevice(
                  deviceId = json.getString("deviceId"),
                  displayName = json.getString("displayName"),
                  state = state,
                  temporaryAccessMinutesRemaining = temporaryMinutes,
                  isOnline = json.optBoolean("isOnline", true),
                  lastSeenAt = json.optNullableString("lastSeenAt"),
                  protectionStatus =
                    DeviceProtectionStatus(
                      accessibilityEnabled = json.optNullableBoolean("accessibilityEnabled"),
                      preciseTimingEnabled = json.optNullableBoolean("preciseTimingEnabled"),
                      batteryUnrestricted = json.optNullableBoolean("batteryUnrestricted"),
                    ),
                ),
              controlToken = controlToken,
            ),
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  private fun savePairingsInternal(pairings: List<PairedChildDevice>) {
    val array = JSONArray()

    pairings.forEach { pairing ->
      val device = pairing.device
      val json =
        JSONObject()
          .put("deviceId", device.deviceId)
          .put("displayName", device.displayName)
          .put("state", device.state.name)
          .put("isOnline", device.isOnline)
          .put(
            "controlTokenEncrypted",
            tokenCipher.encrypt(pairing.controlToken),
          )

      device.temporaryAccessMinutesRemaining?.let {
        json.put("temporaryMinutes", it)
      }
      device.lastSeenAt?.let { json.put("lastSeenAt", it) }
      device.protectionStatus.accessibilityEnabled?.let {
        json.put("accessibilityEnabled", it)
      }
      device.protectionStatus.preciseTimingEnabled?.let {
        json.put("preciseTimingEnabled", it)
      }
      device.protectionStatus.batteryUnrestricted?.let {
        json.put("batteryUnrestricted", it)
      }

      array.put(json)
    }

    preferences.edit().putString(KEY_PAIRED_DEVICES, array.toString()).apply()
  }

  private fun loadLegacyPairedDevice(): ChildDevice? {
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

  private fun loadLegacyControlToken(): String? {
    preferences.getString(KEY_CONTROL_TOKEN_ENCRYPTED, null)?.let { encrypted ->
      return tokenCipher.decrypt(encrypted)
    }

    return preferences.getString(KEY_CONTROL_TOKEN, null)
      ?.takeIf { it.isNotBlank() }
  }

  private companion object {
    const val PREFERENCES_NAME = "phone_guard_parent_settings"

    const val KEY_PAIRED_DEVICES = "paired_devices_v2"
    const val KEY_SELECTED_DEVICE_ID = "selected_device_id"

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

private fun android.content.SharedPreferences.Editor.removeLegacyPairingKeys():
  android.content.SharedPreferences.Editor =
  remove(ParentSettingsLegacyKeys.DEVICE_ID)
    .remove(ParentSettingsLegacyKeys.DISPLAY_NAME)
    .remove(ParentSettingsLegacyKeys.DEVICE_STATE)
    .remove(ParentSettingsLegacyKeys.TEMPORARY_MINUTES)
    .remove(ParentSettingsLegacyKeys.LAST_SEEN_AT)
    .remove(ParentSettingsLegacyKeys.IS_ONLINE)
    .remove(ParentSettingsLegacyKeys.CONTROL_TOKEN)
    .remove(ParentSettingsLegacyKeys.CONTROL_TOKEN_ENCRYPTED)
    .remove(ParentSettingsLegacyKeys.ACCESSIBILITY_ENABLED)
    .remove(ParentSettingsLegacyKeys.PRECISE_TIMING_ENABLED)
    .remove(ParentSettingsLegacyKeys.BATTERY_UNRESTRICTED)

private object ParentSettingsLegacyKeys {
  const val DEVICE_ID = "device_id"
  const val DISPLAY_NAME = "display_name"
  const val DEVICE_STATE = "device_state"
  const val TEMPORARY_MINUTES = "temporary_minutes"
  const val LAST_SEEN_AT = "last_seen_at"
  const val IS_ONLINE = "is_online"
  const val CONTROL_TOKEN = "control_token"
  const val CONTROL_TOKEN_ENCRYPTED = "control_token_encrypted"
  const val ACCESSIBILITY_ENABLED = "accessibility_enabled"
  const val PRECISE_TIMING_ENABLED = "precise_timing_enabled"
  const val BATTERY_UNRESTRICTED = "battery_unrestricted"
}

private fun android.content.SharedPreferences.nullableBoolean(
  key: String,
): Boolean? =
  if (contains(key)) getBoolean(key, false) else null

private fun JSONObject.optNullableBoolean(key: String): Boolean? =
  if (has(key) && !isNull(key)) getBoolean(key) else null

private fun JSONObject.optNullableString(key: String): String? =
  if (has(key) && !isNull(key)) getString(key) else null
