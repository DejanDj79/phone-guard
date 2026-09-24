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

      AppInventorySyncer(applicationContext).sync()
      RemoteCommandSyncer(applicationContext).sync()
    }.start()
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)

    Log.i(
      TAG,
      "FCM message received: keys=" + message.data.keys.sorted().joinToString(","),
    )

    val settingsStore = ChildSettingsStore(applicationContext)
    if (message.data[KEY_TYPE]?.uppercase() == TYPE_TIME_REQUEST_RESULT) {
      when (message.data[KEY_DECISION]?.uppercase()) {
        "DENIED" ->
          settingsStore.setTimeRequestFeedback(
            "Your request for more time was denied by Parent.",
          )
        "APPROVED" ->
          settingsStore.setTimeRequestFeedback(null)
      }
      return
    }

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

        "SYNC_SCHEDULE" -> RemoteCommand.syncSchedule()
        "SYNC_ALLOWED_APPS" -> RemoteCommand.syncAllowedApps()

        else -> return
      }

    RemoteCommandExecutor(applicationContext).applyAndAcknowledge(
      commandId = commandId,
      command = command,
      logTag = TAG,
    )
  }

  override fun onDeletedMessages() {
    super.onDeletedMessages()
    Log.w(TAG, "FCM reported deleted pending messages; syncing backend")

    Thread {
      RemoteCommandSyncer(applicationContext).sync()
    }.start()
  }

  private companion object {
    const val TAG = "PhoneGuardFCM"
    const val KEY_COMMAND = "command"
    const val KEY_COMMAND_ID = "command_id"
    const val KEY_BONUS_MINUTES = "bonus_minutes"
    const val KEY_TYPE = "type"
    const val KEY_DECISION = "decision"
    const val TYPE_TIME_REQUEST_RESULT = "TIME_REQUEST_RESULT"
  }
}
