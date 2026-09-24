package com.example.phoneguard.remote

import android.content.Context
import android.util.Log
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.data.ChildSettingsStore

class RemoteCommandSyncer(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val backendClient = ChildBackendClient()
  private val executor = RemoteCommandExecutor(appContext)

  fun sync() {
    RemoteScheduleSyncer(appContext).sync()
    RemoteAllowedAppsSyncer(appContext).sync()

    val identity = settingsStore.getOrCreatePairingIdentity()
    val deviceSecret = settingsStore.getOrCreateDeviceSecret()

    when (
      val result =
        backendClient.syncCommands(
          deviceId = identity.deviceId,
          deviceSecret = deviceSecret,
        )
    ) {
      is ChildCommandSyncResult.Success -> {
        Log.i(TAG, "Pending command sync: " + result.commands.size + " command(s)")

        result.commands.forEach { pending ->
          val command =
            when (pending.command.uppercase()) {
              "LOCK" -> RemoteCommand.lock()
              "UNLOCK" -> RemoteCommand.unlock()
              "BONUS_TIME" -> {
                val minutes = pending.bonusMinutes
                if (minutes == null || minutes <= 0) {
                  Log.e(TAG, "Invalid BONUS_TIME command: " + pending.commandId)
                  return@forEach
                }

                RemoteCommand.bonusTime(minutes)
              }

              "SYNC_SCHEDULE" -> RemoteCommand.syncSchedule()
              "SYNC_ALLOWED_APPS" -> RemoteCommand.syncAllowedApps()

              else -> {
                Log.e(TAG, "Unknown remote command: " + pending.command)
                return@forEach
              }
            }

          executor.applyAndAcknowledge(
            commandId = pending.commandId,
            command = command,
            logTag = TAG,
          )
        }
      }

      is ChildCommandSyncResult.Failure ->
        Log.w(TAG, "Pending command sync failed: " + result.message)
    }
  }

  companion object {
    const val TAG = "PhoneGuardSync"
  }
}
