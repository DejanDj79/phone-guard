package com.example.phoneguard.core

enum class RemoteCommandType {
  LOCK,
  UNLOCK,
  BONUS_TIME,
  SYNC_SCHEDULE,
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
      RemoteCommandType.SYNC_SCHEDULE ->
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

data class ChildDevice(
  val deviceId: String,
  val displayName: String,
  val state: DeviceAccessState,
  val temporaryAccessMinutesRemaining: Int? = null,
  val isOnline: Boolean = true,
  val lastSeenAt: String? = null,
  val protectionStatus: DeviceProtectionStatus = DeviceProtectionStatus(),
) {
  init {
    require(deviceId.isNotBlank()) { "deviceId must not be blank." }
    require(displayName.isNotBlank()) { "displayName must not be blank." }

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
