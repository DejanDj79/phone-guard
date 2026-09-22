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

    RemoteCommandProcessor(applicationContext).apply(command)
    Log.i(TAG, "Remote command applied: " + command.type.name)
  }

  override fun onDeletedMessages() {
    super.onDeletedMessages()
    Log.w(TAG, "FCM reported deleted pending messages")
  }

  private companion object {
    const val TAG = "PhoneGuardFCM"
    const val KEY_COMMAND = "command"
    const val KEY_BONUS_MINUTES = "bonus_minutes"
  }
}
