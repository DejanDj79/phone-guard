package com.example.phoneguard.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.phoneguard.core.PairingIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ChildRecentAppSession(
  val packageName: String,
  val startedAtMillis: Long,
  val endedAtMillis: Long,
) {
  val durationMillis: Long
    get() = (endedAtMillis - startedAtMillis).coerceAtLeast(0L)
}

class ChildSettingsStore(context: Context) {
  private val preferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val secretCipher = ChildSecretCipher()

  fun hasParentPin(): Boolean =
    preferences.contains(KEY_PIN_HASH) && preferences.contains(KEY_PIN_SALT)

  fun isSetupWizardCompleted(): Boolean =
    preferences.getBoolean(KEY_SETUP_WIZARD_COMPLETED, false)

  fun setSetupWizardCompleted(completed: Boolean) {
    preferences
      .edit()
      .putBoolean(KEY_SETUP_WIZARD_COMPLETED, completed)
      .apply()
  }

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

  fun temporaryAllowanceUntilMillis(): Long =
    preferences.getLong(KEY_TEMPORARY_ALLOW_UNTIL, 0L)

  fun isTemporaryAllowanceActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
    temporaryAllowanceUntilMillis() > nowMillis

  fun grantTemporaryAllowance(
    minutes: Int,
    nowMillis: Long = System.currentTimeMillis(),
  ): Long {
    require(minutes > 0) { "Temporary allowance must be positive." }

    val base = maxOf(nowMillis, temporaryAllowanceUntilMillis())
    val until = base + minutes * 60_000L

    preferences
      .edit()
      .putLong(KEY_TEMPORARY_ALLOW_UNTIL, until)
      .apply()

    return until
  }

  fun clearTemporaryAllowance() {
    preferences.edit().remove(KEY_TEMPORARY_ALLOW_UNTIL).apply()
  }

  fun dailyLimitMinutes(): Int? =
    preferences
      .getInt(KEY_DAILY_LIMIT_MINUTES, 0)
      .takeIf { it > 0 }

  fun remoteDailyLimitVersion(): Long =
    preferences.getLong(KEY_REMOTE_DAILY_LIMIT_VERSION, 0L)

  fun saveRemoteDailyLimit(
    minutes: Int?,
    version: Long,
  ): Boolean {
    require(minutes == null || minutes in 1..1440) {
      "Daily limit must be disabled or between 1 and 1440 minutes."
    }
    require(version >= 0L) { "Daily limit version must not be negative." }

    val editor =
      preferences
        .edit()
        .putLong(KEY_REMOTE_DAILY_LIMIT_VERSION, version)

    if (minutes == null) {
      editor.remove(KEY_DAILY_LIMIT_MINUTES)
    } else {
      editor.putInt(KEY_DAILY_LIMIT_MINUTES, minutes)
    }

    return editor.commit()
  }

  fun currentLocalDateKey(nowMillis: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))

  fun dailyUsageMillisForToday(nowMillis: Long = System.currentTimeMillis()): Long {
    val today = currentLocalDateKey(nowMillis)
    if (preferences.getString(KEY_DAILY_USAGE_DATE, null) != today) {
      return 0L
    }

    return preferences.getLong(KEY_DAILY_USAGE_MILLIS, 0L).coerceAtLeast(0L)
  }

  fun dailyUsageSecondsForToday(nowMillis: Long = System.currentTimeMillis()): Int =
    (dailyUsageMillisForToday(nowMillis) / 1000L)
      .coerceIn(0L, Int.MAX_VALUE.toLong())
      .toInt()

  fun saveDailyUsage(
    date: String,
    usedMillis: Long,
  ): Boolean {
    require(date.matches(LOCAL_DATE_PATTERN)) { "Invalid local date." }
    require(usedMillis >= 0L) { "Daily usage must not be negative." }

    return preferences
      .edit()
      .putString(KEY_DAILY_USAGE_DATE, date)
      .putLong(KEY_DAILY_USAGE_MILLIS, usedMillis)
      .commit()
  }

  fun appUsageMillisForToday(
    nowMillis: Long = System.currentTimeMillis(),
  ): Map<String, Long> {
    val today = currentLocalDateKey(nowMillis)
    if (preferences.getString(KEY_APP_USAGE_DATE, null) != today) {
      return emptyMap()
    }

    val encoded =
      preferences.getString(KEY_APP_USAGE_MILLIS_JSON, null)
        ?.takeIf { it.isNotBlank() }
        ?: return emptyMap()

    return runCatching {
      val json = JSONObject(encoded)
      buildMap {
        val keys = json.keys()
        while (keys.hasNext()) {
          val packageName = keys.next().trim()
          val millis = json.optLong(packageName, 0L).coerceAtLeast(0L)
          if (packageName.isNotBlank() && millis > 0L) {
            put(packageName, millis)
          }
        }
      }
    }.getOrDefault(emptyMap())
  }

  fun saveAppUsage(
    date: String,
    usageMillis: Map<String, Long>,
  ): Boolean {
    require(date.matches(LOCAL_DATE_PATTERN)) { "Invalid local date." }

    val json = JSONObject()
    usageMillis
      .asSequence()
      .filter { (packageName, millis) ->
        packageName.isNotBlank() && millis > 0L
      }
      .sortedByDescending { it.value }
      .take(MAX_TRACKED_APP_USAGE_PACKAGES)
      .forEach { (packageName, millis) ->
        json.put(packageName, millis.coerceAtLeast(0L))
      }

    return preferences
      .edit()
      .putString(KEY_APP_USAGE_DATE, date)
      .putString(KEY_APP_USAGE_MILLIS_JSON, json.toString())
      .commit()
  }


  fun recentAppSessionsForToday(
    nowMillis: Long = System.currentTimeMillis(),
  ): List<ChildRecentAppSession> {
    val today = currentLocalDateKey(nowMillis)
    if (preferences.getString(KEY_RECENT_APP_ACTIVITY_DATE, null) != today) {
      return emptyList()
    }

    val encoded =
      preferences.getString(KEY_RECENT_APP_ACTIVITY_JSON, null)
        ?.takeIf { it.isNotBlank() }
        ?: return emptyList()

    return runCatching {
      val json = JSONArray(encoded)
      buildList {
        for (index in 0 until json.length()) {
          val item = json.optJSONObject(index) ?: continue
          val packageName = item.optString("packageName").trim()
          val startedAtMillis = item.optLong("startedAtMillis", 0L)
          val endedAtMillis = item.optLong("endedAtMillis", 0L)

          if (
            packageName.isNotBlank() &&
            startedAtMillis > 0L &&
            endedAtMillis >= startedAtMillis
          ) {
            add(
              ChildRecentAppSession(
                packageName = packageName,
                startedAtMillis = startedAtMillis,
                endedAtMillis = endedAtMillis,
              ),
            )
          }
        }
      }
    }.getOrDefault(emptyList())
  }

  fun upsertRecentAppSession(
    packageName: String,
    startedAtMillis: Long,
    endedAtMillis: Long,
  ): Boolean {
    val normalizedPackage = packageName.trim()
    if (
      normalizedPackage.isBlank() ||
      startedAtMillis <= 0L ||
      endedAtMillis <= startedAtMillis ||
      endedAtMillis - startedAtMillis < MIN_RECENT_APP_SESSION_MS
    ) {
      return false
    }

    val date = currentLocalDateKey(endedAtMillis)
    val existing =
      if (preferences.getString(KEY_RECENT_APP_ACTIVITY_DATE, null) == date) {
        recentAppSessionsForToday(endedAtMillis)
      } else {
        emptyList()
      }

    val updated =
      buildList {
        add(
          ChildRecentAppSession(
            packageName = normalizedPackage,
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
          ),
        )
        addAll(
          existing.filterNot { session ->
            session.packageName == normalizedPackage &&
              session.startedAtMillis == startedAtMillis
          },
        )
      }
        .sortedByDescending { it.startedAtMillis }
        .take(MAX_RECENT_APP_SESSIONS)

    val json = JSONArray()
    updated.forEach { session ->
      json.put(
        JSONObject()
          .put("packageName", session.packageName)
          .put("startedAtMillis", session.startedAtMillis)
          .put("endedAtMillis", session.endedAtMillis),
      )
    }

    return preferences
      .edit()
      .putString(KEY_RECENT_APP_ACTIVITY_DATE, date)
      .putString(KEY_RECENT_APP_ACTIVITY_JSON, json.toString())
      .commit()
  }

  fun isDailyLimitLockActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val limitMinutes = dailyLimitMinutes() ?: return false
    return dailyUsageMillisForToday(nowMillis) >= limitMinutes * 60_000L
  }

  fun hasAppliedRemoteCommand(commandId: String): Boolean {
    if (commandId.isBlank()) return false

    return appliedRemoteCommandIds().contains(commandId)
  }

  fun markRemoteCommandApplied(commandId: String): Boolean {
    require(commandId.isNotBlank()) { "commandId must not be blank." }

    val updated =
      buildList {
        add(commandId)
        addAll(
          appliedRemoteCommandIds()
            .filterNot { it == commandId },
        )
      }
        .take(MAX_APPLIED_REMOTE_COMMAND_IDS)

    return preferences
      .edit()
      .putString(
        KEY_APPLIED_REMOTE_COMMAND_IDS,
        updated.joinToString("\n"),
      )
      .commit()
  }

  private fun appliedRemoteCommandIds(): List<String> =
    preferences
      .getString(KEY_APPLIED_REMOTE_COMMAND_IDS, "")
      .orEmpty()
      .lineSequence()
      .map { it.trim() }
      .filter(String::isNotEmpty)
      .toList()

  fun isEffectivelyLocked(nowMillis: Long = System.currentTimeMillis()): Boolean =
    !isTemporaryAllowanceActive(nowMillis) &&
      (
        isManualLockActive() ||
          isScheduleLockActive() ||
          isDailyLimitLockActive(nowMillis)
      )

  fun clearAllLocks() {
    preferences
      .edit()
      .putBoolean(KEY_MANUAL_LOCKED, false)
      .putBoolean(KEY_SCHEDULE_LOCKED, false)
      .remove(KEY_TEMPORARY_ALLOW_UNTIL)
      .apply()
  }

  fun getOrCreateDeviceSecret(): String {
    preferences.getString(KEY_DEVICE_SECRET_ENCRYPTED, null)?.let { encrypted ->
      return secretCipher.decrypt(encrypted)
        ?.takeIf { it.isNotBlank() }
        ?: error("Stored device secret could not be decrypted.")
    }

    preferences.getString(KEY_DEVICE_SECRET, null)
      ?.takeIf { it.isNotBlank() }
      ?.let { legacySecret ->
        val encrypted = secretCipher.encrypt(legacySecret)
        val migrated =
          preferences
            .edit()
            .putString(KEY_DEVICE_SECRET_ENCRYPTED, encrypted)
            .remove(KEY_DEVICE_SECRET)
            .commit()

        check(migrated) { "Failed to migrate device secret to encrypted storage." }
        return legacySecret
      }

    val secretBytes = ByteArray(32).also(SecureRandom()::nextBytes)
    val secret =
      Base64.encodeToString(
        secretBytes,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
      )

    val encrypted = secretCipher.encrypt(secret)
    val saved =
      preferences
        .edit()
        .putString(KEY_DEVICE_SECRET_ENCRYPTED, encrypted)
        .remove(KEY_DEVICE_SECRET)
        .commit()

    check(saved) { "Failed to persist encrypted device secret." }
    return secret
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

  fun timeRequestFeedback(): String? =
    preferences.getString(KEY_TIME_REQUEST_FEEDBACK, null)
      ?.takeIf { it.isNotBlank() }

  fun setTimeRequestFeedback(message: String?) {
    val editor = preferences.edit()

    if (message.isNullOrBlank()) {
      editor.remove(KEY_TIME_REQUEST_FEEDBACK)
    } else {
      editor.putString(KEY_TIME_REQUEST_FEEDBACK, message)
    }

    editor.apply()
  }

  fun allowedPackages(): Set<String> =
    preferences
      .getString(KEY_ALLOWED_APPS, "")
      .orEmpty()
      .lineSequence()
      .map { it.trim() }
      .filter(String::isNotEmpty)
      .filterNot(AllowedAppsPolicy::isNeverAllowed)
      .toSet()

  fun isPackageAllowed(packageName: String?): Boolean =
    packageName != null &&
      !AllowedAppsPolicy.isNeverAllowed(packageName) &&
      packageName in allowedPackages()

  fun remoteAllowedAppsVersion(): Long =
    preferences.getLong(KEY_REMOTE_ALLOWED_APPS_VERSION, 0L)

  fun saveRemoteAllowedApps(
    packages: Set<String>,
    version: Long,
  ): Boolean {
    require(version >= 0L) { "Allowed apps version must not be negative." }

    val normalized =
      packages
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot(AllowedAppsPolicy::isNeverAllowed)
        .distinct()
        .sorted()

    return preferences
      .edit()
      .putString(KEY_ALLOWED_APPS, normalized.joinToString("\n"))
      .putLong(KEY_REMOTE_ALLOWED_APPS_VERSION, version)
      .commit()
  }

  fun appInventorySignature(): String =
    preferences.getString(KEY_APP_INVENTORY_SIGNATURE, "").orEmpty()

  fun saveAppInventorySignature(signature: String) {
    preferences
      .edit()
      .putString(KEY_APP_INVENTORY_SIGNATURE, signature)
      .apply()
  }

  fun getWeeklySchedule(): WeeklySchedule =
    WeeklySchedule.decode(preferences.getString(KEY_WEEKLY_SCHEDULE, null))

  fun saveWeeklySchedule(schedule: WeeklySchedule) {
    preferences.edit().putString(KEY_WEEKLY_SCHEDULE, schedule.encode()).apply()
  }

  fun remoteScheduleVersion(): Long =
    preferences.getLong(KEY_REMOTE_SCHEDULE_VERSION, 0L)

  fun saveRemoteWeeklySchedule(
    schedule: WeeklySchedule,
    version: Long,
  ): Boolean {
    require(version >= 0L) { "Schedule version must not be negative." }

    return preferences
      .edit()
      .putString(KEY_WEEKLY_SCHEDULE, schedule.encode())
      .putLong(KEY_REMOTE_SCHEDULE_VERSION, version)
      .commit()
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
        if (
          key == KEY_MANUAL_LOCKED ||
          key == KEY_SCHEDULE_LOCKED ||
          key == KEY_TEMPORARY_ALLOW_UNTIL ||
          key == KEY_ALLOWED_APPS ||
          key == KEY_TIME_REQUEST_FEEDBACK ||
          key == KEY_DAILY_LIMIT_MINUTES ||
          key == KEY_DAILY_USAGE_DATE ||
          key == KEY_DAILY_USAGE_MILLIS
        ) {
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
    const val KEY_SETUP_WIZARD_COMPLETED = "setup_wizard_completed"
    const val KEY_MANUAL_LOCKED = "manual_locked"
    const val KEY_SCHEDULE_LOCKED = "schedule_locked"
    const val KEY_WEEKLY_SCHEDULE = "weekly_schedule"
    const val KEY_REMOTE_SCHEDULE_VERSION = "remote_schedule_version"
    const val KEY_ALLOWED_APPS = "allowed_apps"
    const val KEY_REMOTE_ALLOWED_APPS_VERSION = "remote_allowed_apps_version"
    const val KEY_APP_INVENTORY_SIGNATURE = "app_inventory_signature"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_PAIRING_CODE = "pairing_code"
    const val KEY_DEVICE_SECRET = "device_secret"
    const val KEY_DEVICE_SECRET_ENCRYPTED = "device_secret_encrypted"
    const val KEY_TEMPORARY_ALLOW_UNTIL = "temporary_allow_until"
    const val KEY_APPLIED_REMOTE_COMMAND_IDS = "applied_remote_command_ids"
    const val KEY_TIME_REQUEST_FEEDBACK = "time_request_feedback"
    const val KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes"
    const val KEY_REMOTE_DAILY_LIMIT_VERSION = "remote_daily_limit_version"
    const val KEY_DAILY_USAGE_DATE = "daily_usage_date"
    const val KEY_DAILY_USAGE_MILLIS = "daily_usage_millis"
    const val KEY_APP_USAGE_DATE = "app_usage_date"
    const val KEY_APP_USAGE_MILLIS_JSON = "app_usage_millis_json"
    const val KEY_RECENT_APP_ACTIVITY_DATE = "recent_app_activity_date"
    const val KEY_RECENT_APP_ACTIVITY_JSON = "recent_app_activity_json"
    const val MAX_TRACKED_APP_USAGE_PACKAGES = 100
    const val MAX_RECENT_APP_SESSIONS = 20
    const val MIN_RECENT_APP_SESSION_MS = 1_000L
    const val MAX_APPLIED_REMOTE_COMMAND_IDS = 100
    const val SALT_SIZE_BYTES = 16
    const val PAIRING_CODE_LENGTH = 6
    const val PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    val PIN_PATTERN = Regex("^\\d{4,6}$")
    val LOCAL_DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
  }
}
