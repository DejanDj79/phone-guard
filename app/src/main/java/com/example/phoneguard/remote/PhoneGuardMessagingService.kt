package com.example.phoneguard.remote

import android.os.Build
import android.util.Log
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.data.ChildSettingsStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PhoneGuardMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.i(TAG, "FCM token refreshed")

    Thread {
      val settingsStore = ChildSettingsStore(applicationContext)

      ChildBackendClient().register(
        identity = settingsStore.getOrCreatePairingIdentity(),
        displayName = Build.MODEL.ifBlank { "Child device" },
        deviceSecret = settingsStore.getOrCreateDeviceSecret(),
        fcmToken = token,
      )
    }.start()
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)

    Log.i(
      TAG,
      "FCM message received: keys=" + message.data.keys.sorted().joinToString(","),
    )

    val commandId = message.data[KEY_COMMAND_ID]?.trim().orEmpty()

    val command =
      when (message.data[KEY_COMMAND]?.uppercase()) {
        "LOCK" -> RemoteCommand.lock()
        "UNLOCK" -> RemoteCommand.unlock()
        "BONUS_TIME" -> {
          val minutes =
            message.data[KEY_BONUS_MINUTES]
              ?.toIntOrNull()
              ?.takeIf { it > 0 }
              ?: return

          RemoteCommand.bonusTime(minutes)
        }

        else -> return
      }

    val settingsStore = ChildSettingsStore(applicationContext)
    val alreadyApplied =
      commandId.isNotBlank() &&
        settingsStore.hasAppliedRemoteCommand(commandId)

    if (alreadyApplied) {
      Log.i(TAG, "Duplicate command skipped: " + commandId)
    } else {
      RemoteCommandProcessor(applicationContext).apply(command)
      Log.i(TAG, "Remote command applied: " + command.type.name)

      if (
        commandId.isNotBlank() &&
        !settingsStore.markRemoteCommandApplied(commandId)
      ) {
        Log.e(TAG, "Failed to persist applied command id: " + commandId)
      }
    }

    if (commandId.isBlank()) {
      Log.w(TAG, "Command has no command_id; ACK skipped")
      return
    }

    when (
      val ackResult =
        ChildBackendClient().acknowledgeCommand(
          deviceId = settingsStore.getOrCreatePairingIdentity().deviceId,
          deviceSecret = settingsStore.getOrCreateDeviceSecret(),
          commandId = commandId,
        )
    ) {
      ChildCommandAckResult.Success ->
        Log.i(TAG, "Command ACK applied: " + commandId)

      is ChildCommandAckResult.Failure ->
        Log.e(
          TAG,
          "Command ACK failed: " + ackResult.message,
        )
    }
  }

  override fun onDeletedMessages() {
    super.onDeletedMessages()
    Log.w(TAG, "FCM reported deleted pending messages")
  }

  private companion object {
    const val TAG = "PhoneGuardFCM"
    const val KEY_COMMAND = "command"
    const val KEY_COMMAND_ID = "command_id"
    const val KEY_BONUS_MINUTES = "bonus_minutes"
  }
}
