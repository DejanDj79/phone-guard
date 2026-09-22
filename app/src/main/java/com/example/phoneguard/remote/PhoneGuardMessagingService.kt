package com.example.phoneguard.remote

import android.os.Build
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.data.ChildSettingsStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PhoneGuardMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    super.onNewToken(token)

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
  }

  private companion object {
    const val KEY_COMMAND = "command"
    const val KEY_BONUS_MINUTES = "bonus_minutes"
  }
}
