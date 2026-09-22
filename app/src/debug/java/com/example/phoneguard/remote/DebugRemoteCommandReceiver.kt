package com.example.phoneguard.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.phoneguard.core.RemoteCommand

class DebugRemoteCommandReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val commandName = intent?.getStringExtra(EXTRA_COMMAND) ?: return
    val processor = RemoteCommandProcessor(context)

    when (commandName.uppercase()) {
      "LOCK" -> processor.apply(RemoteCommand.lock())
      "UNLOCK" -> processor.apply(RemoteCommand.unlock())
      "BONUS_TIME" -> {
        val minutes = intent.getIntExtra(EXTRA_BONUS_MINUTES, 0)
        if (minutes > 0) {
          processor.apply(RemoteCommand.bonusTime(minutes))
        }
      }
    }
  }

  private companion object {
    const val EXTRA_COMMAND = "command"
    const val EXTRA_BONUS_MINUTES = "bonus_minutes"
  }
}
