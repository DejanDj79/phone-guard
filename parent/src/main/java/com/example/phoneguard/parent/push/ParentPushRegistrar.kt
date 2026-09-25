package com.example.phoneguard.parent.push

import android.content.Context
import android.util.Log
import com.example.phoneguard.parent.data.ParentSettingsStore
import com.example.phoneguard.parent.data.applyParentAuthHeaders
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ParentPushRegistrar(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ParentSettingsStore(appContext)

  fun registerCurrentToken() {
    if (FirebaseApp.getApps(appContext).isEmpty()) {
      Log.i(TAG, "Firebase is not configured for Parent app yet")
      return
    }

    FirebaseMessaging.getInstance().token
      .addOnCompleteListener { task ->
        if (!task.isSuccessful) {
          Log.w(TAG, "Could not obtain Parent FCM token", task.exception)
          return@addOnCompleteListener
        }

        val token = task.result?.trim().orEmpty()
        if (token.isEmpty()) return@addOnCompleteListener

        registerToken(token)
      }
  }

  fun registerToken(token: String) {
    if (token.isBlank()) return

    Thread {
      settingsStore.loadPairedDevices().forEach { device ->
        val controlToken =
          settingsStore.controlToken(device.deviceId)
            ?: return@forEach

        val registered =
          registerForDevice(
            deviceId = device.deviceId,
            controlToken = controlToken,
            fcmToken = token,
          )

        if (!registered) {
          Log.w(TAG, "Parent FCM token registration failed for " + device.deviceId)
        }
      }
    }.start()
  }

  private fun registerForDevice(
    deviceId: String,
    controlToken: String,
    fcmToken: String,
  ): Boolean {
    val connection =
      (URL(REGISTER_PARENT_PUSH_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        applyParentAuthHeaders()
      }

    return try {
      val body =
        JSONObject()
          .put("deviceId", deviceId)
          .put("controlToken", controlToken)
          .put("fcmToken", fcmToken)
          .toString()

      connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(body)
      }

      connection.responseCode in 200..299
    } catch (error: Exception) {
      Log.w(TAG, "Parent push registration error", error)
      false
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val TAG = "PhoneGuardParentPush"
    const val REGISTER_PARENT_PUSH_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/register-parent-push"
  }
}
