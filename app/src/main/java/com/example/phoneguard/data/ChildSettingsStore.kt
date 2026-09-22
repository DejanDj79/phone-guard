package com.example.phoneguard.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.phoneguard.core.PairingIdentity
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import java.util.UUID

class ChildSettingsStore(context: Context) {
  private val preferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun hasParentPin(): Boolean =
    preferences.contains(KEY_PIN_HASH) && preferences.contains(KEY_PIN_SALT)

  fun setParentPin(pin: String) {
    require(PIN_PATTERN.matches(pin)) { "PIN must contain 4 to 6 digits." }

    val salt = ByteArray(SALT_SIZE_BYTES).also(SecureRandom()::nextBytes)
    val hash = hashPin(pin, salt)

    preferences
      .edit()
      .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
      .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
      .apply()
  }

  fun verifyParentPin(pin: String): Boolean {
    val saltEncoded = preferences.getString(KEY_PIN_SALT, null) ?: return false
    val hashEncoded = preferences.getString(KEY_PIN_HASH, null) ?: return false

    return runCatching {
      val salt = Base64.decode(saltEncoded, Base64.NO_WRAP)
      val expectedHash = Base64.decode(hashEncoded, Base64.NO_WRAP)
      val actualHash = hashPin(pin, salt)
      MessageDigest.isEqual(expectedHash, actualHash)
    }.getOrDefault(false)
  }

  fun isManualLockActive(): Boolean =
    preferences.getBoolean(KEY_MANUAL_LOCKED, false)

  fun setManualLock(locked: Boolean) {
    preferences.edit().putBoolean(KEY_MANUAL_LOCKED, locked).apply()
  }

  fun isScheduleLockActive(): Boolean =
    preferences.getBoolean(KEY_SCHEDULE_LOCKED, false)

  fun setScheduleLock(locked: Boolean) {
    preferences.edit().putBoolean(KEY_SCHEDULE_LOCKED, locked).apply()
  }

  fun isEffectivelyLocked(): Boolean =
    isManualLockActive() || isScheduleLockActive()

  fun clearAllLocks() {
    preferences
      .edit()
      .putBoolean(KEY_MANUAL_LOCKED, false)
      .putBoolean(KEY_SCHEDULE_LOCKED, false)
      .apply()
  }

  fun getOrCreatePairingIdentity(): PairingIdentity {
    val existingDeviceId = preferences.getString(KEY_DEVICE_ID, null)
    val existingPairingCode = preferences.getString(KEY_PAIRING_CODE, null)

    if (
      !existingDeviceId.isNullOrBlank() &&
      !existingPairingCode.isNullOrBlank()
    ) {
      return PairingIdentity(
        deviceId = existingDeviceId,
        pairingCode = existingPairingCode,
      )
    }

    val identity =
      PairingIdentity(
        deviceId = UUID.randomUUID().toString(),
        pairingCode = generatePairingCode(),
      )

    preferences
      .edit()
      .putString(KEY_DEVICE_ID, identity.deviceId)
      .putString(KEY_PAIRING_CODE, identity.pairingCode)
      .apply()

    return identity
  }

  fun regeneratePairingCode(): PairingIdentity {
    val current = getOrCreatePairingIdentity()
    val refreshed =
      current.copy(pairingCode = generatePairingCode())

    preferences
      .edit()
      .putString(KEY_PAIRING_CODE, refreshed.pairingCode)
      .apply()

    return refreshed
  }

  fun getWeeklySchedule(): WeeklySchedule =
    WeeklySchedule.decode(preferences.getString(KEY_WEEKLY_SCHEDULE, null))

  fun saveWeeklySchedule(schedule: WeeklySchedule) {
    preferences.edit().putString(KEY_WEEKLY_SCHEDULE, schedule.encode()).apply()
  }

  fun shouldBeRestrictedNow(calendar: Calendar = Calendar.getInstance()): Boolean =
    getWeeklySchedule().isRestrictedAt(calendar)

  fun currentScheduledUnlockLabel(calendar: Calendar = Calendar.getInstance()): String? =
    getWeeklySchedule().currentUnlockTimeLabel(calendar)

  fun registerLockStateListener(
    onChanged: () -> Unit,
  ): SharedPreferences.OnSharedPreferenceChangeListener {
    val listener =
      SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_MANUAL_LOCKED || key == KEY_SCHEDULE_LOCKED) {
          onChanged()
        }
      }
    preferences.registerOnSharedPreferenceChangeListener(listener)
    return listener
  }

  fun unregisterLockStateListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    preferences.unregisterOnSharedPreferenceChangeListener(listener)
  }

  private fun generatePairingCode(): String =
    buildString(PAIRING_CODE_LENGTH) {
      repeat(PAIRING_CODE_LENGTH) {
        append(PAIRING_ALPHABET[SecureRandom().nextInt(PAIRING_ALPHABET.length)])
      }
    }

  private fun hashPin(pin: String, salt: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").run {
      update(salt)
      digest(pin.toByteArray(Charsets.UTF_8))
    }

  private companion object {
    const val PREFERENCES_NAME = "phone_guard_child_settings"
    const val KEY_PIN_SALT = "parent_pin_salt"
    const val KEY_PIN_HASH = "parent_pin_hash"
    const val KEY_MANUAL_LOCKED = "manual_locked"
    const val KEY_SCHEDULE_LOCKED = "schedule_locked"
    const val KEY_WEEKLY_SCHEDULE = "weekly_schedule"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_PAIRING_CODE = "pairing_code"
    const val SALT_SIZE_BYTES = 16
    const val PAIRING_CODE_LENGTH = 6
    const val PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    val PIN_PATTERN = Regex("^\\d{4,6}$")
  }
}
