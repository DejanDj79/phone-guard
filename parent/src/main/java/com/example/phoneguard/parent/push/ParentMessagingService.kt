package com.example.phoneguard.parent.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
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

    when (message.data["type"]) {
      TYPE_TIME_REQUEST -> showTimeRequestNotification(message)
      TYPE_PROTECTION_ALERT -> showProtectionAlertNotification(message)
    }
  }

  private fun showTimeRequestNotification(message: RemoteMessage) {
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

    ensureTimeRequestChannel()

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
      NotificationCompat.Builder(this, TIME_REQUEST_CHANNEL_ID)
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

  private fun showProtectionAlertNotification(message: RemoteMessage) {
    Log.i(TAG, "Protection alert received: " + message.data)

    val alert =
      message.data["alert"]
        ?.takeIf { it.isNotBlank() }
        ?: return
    val displayName =
      message.data["display_name"]
        ?.takeIf { it.isNotBlank() }
        ?: "Child device"
    val deviceId =
      message.data["device_id"]
        ?.takeIf { it.isNotBlank() }
        ?: return

    val title: String
    val body: String

    when (alert) {
      ALERT_ACCESSIBILITY_DISABLED -> {
        title = "PhoneGuard protection disabled"
        body = "Accessibility protection was disabled on " + displayName + "."
      }

      ALERT_PRECISE_TIMING_DISABLED -> {
        title = "PhoneGuard protection warning"
        body = "Precise timing was disabled on " + displayName + "."
      }

      ALERT_BATTERY_UNRESTRICTED_DISABLED -> {
        title = "PhoneGuard protection warning"
        body = "Battery unrestricted access was disabled on " + displayName + "."
      }

      ALERT_CHILD_OFFLINE -> {
        title = displayName + " is offline"
        body = "No heartbeat received for 5 minutes."
      }

      else -> return
    }

    ensureProtectionAlertsChannel()

    val notificationKey = "protection:" + alert + ":" + deviceId
    val intent =
      Intent(this, MainActivity::class.java).apply {
        flags =
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
      }

    val pendingIntent =
      PendingIntent.getActivity(
        this,
        notificationKey.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(this, PROTECTION_ALERTS_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(notificationKey.hashCode(), notification)
  }

  private fun ensureTimeRequestChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(TIME_REQUEST_CHANNEL_ID) != null) return

    manager.createNotificationChannel(
      NotificationChannel(
        TIME_REQUEST_CHANNEL_ID,
        "Time requests",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Requests from Child devices for additional screen time."
      },
    )
  }

  private fun ensureProtectionAlertsChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(PROTECTION_ALERTS_CHANNEL_ID) != null) return

    manager.createNotificationChannel(
      NotificationChannel(
        PROTECTION_ALERTS_CHANNEL_ID,
        "Protection alerts",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Alerts when Child protection settings change or a Child device goes offline."
      },
    )
  }

  companion object {
    private const val TAG = "PhoneGuardParentPush"
    const val EXTRA_TIME_REQUEST_DEVICE_ID = "time_request_device_id"
    const val EXTRA_TIME_REQUEST_ID = "time_request_id"
    private const val TYPE_TIME_REQUEST = "TIME_REQUEST"
    private const val TYPE_PROTECTION_ALERT = "PROTECTION_ALERT"
    private const val ALERT_ACCESSIBILITY_DISABLED = "ACCESSIBILITY_DISABLED"
    private const val ALERT_PRECISE_TIMING_DISABLED = "PRECISE_TIMING_DISABLED"
    private const val ALERT_BATTERY_UNRESTRICTED_DISABLED = "BATTERY_UNRESTRICTED_DISABLED"
    private const val ALERT_CHILD_OFFLINE = "CHILD_OFFLINE"
    private const val TIME_REQUEST_CHANNEL_ID = "phoneguard_time_requests"
    private const val PROTECTION_ALERTS_CHANNEL_ID = "phoneguard_protection_alerts"
  }
}
