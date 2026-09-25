package com.example.phoneguard.remote

import android.content.Context
import android.util.Log
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.data.ChildSettingsStore

class RemoteCommandExecutor(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val backendClient = ChildBackendClient()

  fun applyAndAcknowledge(
    commandId: String,
    command: RemoteCommand,
    logTag: String,
  ) {
    synchronized(executionLock) {
      val alreadyApplied =
        commandId.isNotBlank() &&
          settingsStore.hasAppliedRemoteCommand(commandId)

      if (alreadyApplied) {
        Log.i(logTag, "Duplicate command skipped: " + commandId)
      } else {
        val applied =
          runCatching {
            RemoteCommandProcessor(appContext).apply(command)
          }.onFailure { error ->
            Log.e(
              logTag,
              "Remote command failed: " + command.type.name,
              error,
            )
          }.isSuccess

        if (!applied) {
          return
        }

        Log.i(logTag, "Remote command applied: " + command.type.name)

        if (
          commandId.isNotBlank() &&
          !settingsStore.markRemoteCommandApplied(commandId)
        ) {
          Log.e(logTag, "Failed to persist applied command id: " + commandId)
        }
      }
    }

    if (commandId.isBlank()) {
      Log.w(logTag, "Command has no command_id; ACK skipped")
      return
    }

    when (
      val ackResult =
        backendClient.acknowledgeCommand(
          deviceId = settingsStore.getOrCreatePairingIdentity().deviceId,
          deviceSecret = settingsStore.getOrCreateDeviceSecret(),
          commandId = commandId,
        )
    ) {
      ChildCommandAckResult.Success ->
        Log.i(logTag, "Command ACK applied: " + commandId)

      is ChildCommandAckResult.Failure ->
        Log.e(
          logTag,
          "Command ACK failed: " + ackResult.message,
        )
    }
  }

  private companion object {
    val executionLock = Any()
  }
}
