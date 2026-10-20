package com.example.phoneguard.core

enum class RemoteCommandType {
  LOCK,
  UNLOCK,
  BONUS_TIME,
  SYNC_SCHEDULE,
  SYNC_ALLOWED_APPS,
  SYNC_DAILY_LIMIT,
  SYNC_GEOFENCES,
  REQUEST_LOCATION,
}

data class RemoteCommand(
  val type: RemoteCommandType,
  val bonusMinutes: Int? = null,
) {
  init {
    when (type) {
      RemoteCommandType.BONUS_TIME ->
        require(bonusMinutes != null && bonusMinutes > 0) {
          "BONUS_TIME requires a positive duration."
        }

      RemoteCommandType.LOCK,
      RemoteCommandType.UNLOCK,
      RemoteCommandType.SYNC_SCHEDULE,
      RemoteCommandType.SYNC_ALLOWED_APPS,
      RemoteCommandType.SYNC_DAILY_LIMIT,
      RemoteCommandType.SYNC_GEOFENCES,
      RemoteCommandType.REQUEST_LOCATION ->
        require(bonusMinutes == null) {
          "$type must not include bonusMinutes."
        }
    }
  }

  companion object {
    fun lock(): RemoteCommand =
      RemoteCommand(RemoteCommandType.LOCK)

    fun unlock(): RemoteCommand =
      RemoteCommand(RemoteCommandType.UNLOCK)

    fun bonusTime(minutes: Int): RemoteCommand =
      RemoteCommand(
        type = RemoteCommandType.BONUS_TIME,
        bonusMinutes = minutes,
      )

    fun syncSchedule(): RemoteCommand =
      RemoteCommand(RemoteCommandType.SYNC_SCHEDULE)

    fun syncAllowedApps(): RemoteCommand =
      RemoteCommand(RemoteCommandType.SYNC_ALLOWED_APPS)

    fun syncDailyLimit(): RemoteCommand =
      RemoteCommand(RemoteCommandType.SYNC_DAILY_LIMIT)

    fun syncGeofences(): RemoteCommand =
      RemoteCommand(RemoteCommandType.SYNC_GEOFENCES)

    fun requestLocation(): RemoteCommand =
      RemoteCommand(RemoteCommandType.REQUEST_LOCATION)
  }
}

enum class DeviceAccessState {
  ALLOWED,
  LOCKED,
  TEMPORARILY_ALLOWED,
  OFFLINE,
}

data class DeviceProtectionStatus(
  val accessibilityEnabled: Boolean? = null,
  val preciseTimingEnabled: Boolean? = null,
  val batteryUnrestricted: Boolean? = null,
) {
  val criticalProtectionComplete: Boolean?
    get() =
      if (accessibilityEnabled == null || preciseTimingEnabled == null) {
        null
      } else {
        accessibilityEnabled && preciseTimingEnabled
      }
}

data class DailyScreenTimeStatus(
  val limitMinutes: Int? = null,
  val usedSeconds: Int = 0,
  val remainingMinutes: Int? = null,
  val usageDate: String? = null,
  val limitReached: Boolean = false,
) {
  init {
    require(limitMinutes == null || limitMinutes > 0) {
      "Daily screen time limit must be positive when enabled."
    }
    require(usedSeconds >= 0) {
      "Daily used seconds must not be negative."
    }
    require(remainingMinutes == null || remainingMinutes >= 0) {
      "Daily remaining minutes must not be negative."
    }
  }
}

data class ChildDevice(
  val deviceId: String,
  val displayName: String,
  val state: DeviceAccessState,
  val temporaryAccessMinutesRemaining: Int? = null,
  val temporaryAccessGrantedMinutes: Int? = null,
  val isOnline: Boolean = true,
  val lastSeenAt: String? = null,
  val batteryLevelPercent: Int? = null,
  val protectionStatus: DeviceProtectionStatus = DeviceProtectionStatus(),
  val dailyScreenTime: DailyScreenTimeStatus = DailyScreenTimeStatus(),
) {
  init {
    require(deviceId.isNotBlank()) { "deviceId must not be blank." }
    require(displayName.isNotBlank()) { "displayName must not be blank." }
    require(batteryLevelPercent == null || batteryLevelPercent in 0..100) {
      "batteryLevelPercent must be between 0 and 100 when available."
    }
    require(
      temporaryAccessGrantedMinutes == null ||
        temporaryAccessGrantedMinutes > 0,
    ) {
      "temporaryAccessGrantedMinutes must be positive when available."
    }

    if (state == DeviceAccessState.TEMPORARILY_ALLOWED) {
      require(
        temporaryAccessMinutesRemaining != null &&
          temporaryAccessMinutesRemaining >= 0,
      ) {
        "TEMPORARILY_ALLOWED requires non-negative remaining minutes."
      }
    }
  }
}

data class PairingIdentity(
  val deviceId: String,
  val pairingCode: String,
) {
  init {
    require(deviceId.isNotBlank()) { "deviceId must not be blank." }
    require(pairingCode.matches(Regex("^[A-Z0-9]{6}$"))) {
      "pairingCode must contain exactly 6 uppercase letters or digits."
    }
  }
}


data class PairingRequest(
  val pairingCode: String,
) {
  init {
    require(pairingCode.matches(Regex("^[A-Z0-9]{6}$"))) {
      "pairingCode must contain exactly 6 uppercase letters or digits."
    }
  }

  companion object {
    fun fromUserInput(value: String): PairingRequest =
      PairingRequest(
        pairingCode =
          value
            .trim()
            .uppercase()
            .filter(Char::isLetterOrDigit),
      )
  }
}

sealed interface PairingResult {
  data class Success(
    val device: ChildDevice,
    val controlToken: String? = null,
  ) : PairingResult

  data class InvalidCode(
    val message: String = "Pairing code is invalid or expired.",
  ) : PairingResult

  data class Error(
    val message: String,
  ) : PairingResult
}


data class GeofenceZone(
  val zoneId: String,
  val name: String,
  val latitude: Double,
  val longitude: Double,
  val radiusMeters: Int,
  val enabled: Boolean = true,
) {
  init {
    require(zoneId.isNotBlank()) { "zoneId must not be blank." }
    require(name.isNotBlank() && name.length <= 60) {
      "Geofence name must contain 1 to 60 characters."
    }
    require(latitude in -90.0..90.0) {
      "Geofence latitude must be between -90 and 90."
    }
    require(longitude in -180.0..180.0) {
      "Geofence longitude must be between -180 and 180."
    }
    require(radiusMeters in 50..5000) {
      "Geofence radius must be between 50 and 5000 meters."
    }
  }
}

enum class GeofenceTransitionType {
  ENTER,
  EXIT,
}
