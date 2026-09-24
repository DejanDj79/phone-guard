package com.example.phoneguard.parent.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.phoneguard.parent.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ParentMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    super.onNewToken(token)
    ParentPushRegistrar(applicationContext).registerToken(token)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)

    if (message.data["type"] != TYPE_TIME_REQUEST) return

    val displayName =
      message.data["display_name"]
        ?.takeIf { it.isNotBlank() }
        ?: "Child device"
    val minutes =
      message.data["requested_minutes"]
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: return
    val requestId =
      message.data["request_id"]
        ?.takeIf { it.isNotBlank() }
        ?: return

    ensureChannel()

    val intent =
      Intent(this, MainActivity::class.java).apply {
        flags =
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_TIME_REQUEST_DEVICE_ID, message.data["device_id"])
        putExtra(EXTRA_TIME_REQUEST_ID, requestId)
      }

    val pendingIntent =
      PendingIntent.getActivity(
        this,
        requestId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("More time requested")
        .setContentText(
          displayName + " is asking for " + minutes + " more minutes.",
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(requestId.hashCode(), notification)
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return

    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        "Time requests",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Requests from Child devices for additional screen time."
      },
    )
  }

  companion object {
    const val EXTRA_TIME_REQUEST_DEVICE_ID = "time_request_device_id"
    const val EXTRA_TIME_REQUEST_ID = "time_request_id"
    private const val TYPE_TIME_REQUEST = "TIME_REQUEST"
    private const val CHANNEL_ID = "phoneguard_time_requests"
  }
}
