package com.example.phoneguard.core

enum class RemoteCommandType {
  LOCK,
  UNLOCK,
  BONUS_TIME,
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
      RemoteCommandType.UNLOCK ->
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
  }
}

enum class DeviceAccessState {
  ALLOWED,
  LOCKED,
  TEMPORARILY_ALLOWED,
  OFFLINE,
}

data class ChildDevice(
  val deviceId: String,
  val displayName: String,
  val state: DeviceAccessState,
  val temporaryAccessMinutesRemaining: Int? = null,
) {
  init {
    require(deviceId.isNotBlank()) { "deviceId must not be blank." }
    require(displayName.isNotBlank()) { "displayName must not be blank." }

    if (state == DeviceAccessState.TEMPORARILY_ALLOWED) {
      require(
        temporaryAccessMinutesRemaining != null &&
          temporaryAccessMinutesRemaining > 0,
      ) {
        "TEMPORARILY_ALLOWED requires remaining minutes."
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
  ) : PairingResult

  data class InvalidCode(
    val message: String = "Pairing code is invalid or expired.",
  ) : PairingResult

  data class Error(
    val message: String,
  ) : PairingResult
}
