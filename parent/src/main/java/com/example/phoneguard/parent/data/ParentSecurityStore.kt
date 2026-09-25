package com.example.phoneguard.parent.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

class ParentSecurityStore(context: Context) {
  private val preferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun hasPin(): Boolean =
    preferences.contains(KEY_PIN_HASH) &&
      preferences.contains(KEY_PIN_SALT)

  fun setPin(pin: String) {
    require(PIN_PATTERN.matches(pin)) {
      "PIN must contain 4 to 6 digits."
    }

    val salt = ByteArray(SALT_SIZE_BYTES).also(SecureRandom()::nextBytes)
    val hash = hashPin(pin, salt)

    preferences
      .edit()
      .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
      .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
      .apply()
  }

  fun biometricEnabled(): Boolean =
    preferences.getBoolean(KEY_BIOMETRIC_ENABLED, true)

  fun setBiometricEnabled(enabled: Boolean) {
    preferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
  }

  fun relockAfterBackgroundMillis(): Long {
    val stored =
      preferences.getLong(
        KEY_RELOCK_AFTER_BACKGROUND_MS,
        DEFAULT_RELOCK_AFTER_BACKGROUND_MS,
      )

    return stored.takeIf { it in ALLOWED_RELOCK_DELAYS_MS }
      ?: DEFAULT_RELOCK_AFTER_BACKGROUND_MS
  }

  fun setRelockAfterBackgroundMillis(delayMillis: Long) {
    require(delayMillis in ALLOWED_RELOCK_DELAYS_MS) {
      "Unsupported Parent relock delay."
    }

    preferences
      .edit()
      .putLong(KEY_RELOCK_AFTER_BACKGROUND_MS, delayMillis)
      .apply()
  }

  fun verifyPin(pin: String): Boolean {
    val saltEncoded = preferences.getString(KEY_PIN_SALT, null) ?: return false
    val hashEncoded = preferences.getString(KEY_PIN_HASH, null) ?: return false

    return runCatching {
      val salt = Base64.decode(saltEncoded, Base64.NO_WRAP)
      val expectedHash = Base64.decode(hashEncoded, Base64.NO_WRAP)
      val actualHash = hashPin(pin, salt)
      MessageDigest.isEqual(expectedHash, actualHash)
    }.getOrDefault(false)
  }

  private fun hashPin(
    pin: String,
    salt: ByteArray,
  ): ByteArray =
    MessageDigest.getInstance("SHA-256").run {
      update(salt)
      digest(pin.toByteArray(Charsets.UTF_8))
    }

  private companion object {
    const val PREFERENCES_NAME = "phone_guard_parent_security"
    const val KEY_PIN_SALT = "pin_salt"
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    const val KEY_RELOCK_AFTER_BACKGROUND_MS = "relock_after_background_ms"
    const val SALT_SIZE_BYTES = 16
    const val DEFAULT_RELOCK_AFTER_BACKGROUND_MS = 30_000L
    val ALLOWED_RELOCK_DELAYS_MS = setOf(0L, 30_000L, 60_000L, 300_000L)
    val PIN_PATTERN = Regex("^\\d{4,6}$")
  }
}
