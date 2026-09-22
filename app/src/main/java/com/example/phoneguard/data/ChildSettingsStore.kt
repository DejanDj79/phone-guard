package com.example.phoneguard.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar

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

  fun isLocked(): Boolean = preferences.getBoolean(KEY_IS_LOCKED, false)

  fun setLocked(locked: Boolean) {
    preferences.edit().putBoolean(KEY_IS_LOCKED, locked).apply()
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

  private fun hashPin(pin: String, salt: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").run {
      update(salt)
      digest(pin.toByteArray(Charsets.UTF_8))
    }

  private companion object {
    const val PREFERENCES_NAME = "phone_guard_child_settings"
    const val KEY_PIN_SALT = "parent_pin_salt"
    const val KEY_PIN_HASH = "parent_pin_hash"
    const val KEY_IS_LOCKED = "is_locked"
    const val KEY_WEEKLY_SCHEDULE = "weekly_schedule"
    const val SALT_SIZE_BYTES = 16

    val PIN_PATTERN = Regex("^\\d{4,6}$")
  }
}
