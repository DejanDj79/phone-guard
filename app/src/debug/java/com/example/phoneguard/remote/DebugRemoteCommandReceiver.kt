package com.example.phoneguard.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.phoneguard.core.RemoteCommand

class DebugRemoteCommandReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    intent ?: return

    val commandId = intent.getStringExtra(EXTRA_COMMAND_ID)?.trim().orEmpty()
    val commandName = intent.getStringExtra(EXTRA_COMMAND)?.trim()?.uppercase().orEmpty()

    val command =
      when (commandName) {
        "LOCK" -> RemoteCommand.lock()
        "UNLOCK" -> RemoteCommand.unlock()
        "BONUS_TIME" -> {
          val minutes = intent.getIntExtra(EXTRA_BONUS_MINUTES, 0)
          if (minutes <= 0) {
            Log.e(TAG, "Invalid BONUS_TIME minutes")
            return
          }

          RemoteCommand.bonusTime(minutes)
        }

        else -> {
          Log.e(TAG, "Unknown debug command: " + commandName)
          return
        }
      }

    Thread {
      RemoteCommandExecutor(context.applicationContext).applyAndAcknowledge(
        commandId = commandId,
        command = command,
        logTag = TAG,
      )
    }.start()
  }

  private companion object {
    const val TAG = "PhoneGuardDebug"
    const val EXTRA_COMMAND_ID = "command_id"
    const val EXTRA_COMMAND = "command"
    const val EXTRA_BONUS_MINUTES = "bonus_minutes"
  }
}
