package com.example.phoneguard.parent.data

import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.DeviceProtectionStatus
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface DeviceStatusResult {
  data class Success(
    val device: ChildDevice,
  ) : DeviceStatusResult

  data class Error(
    val message: String,
  ) : DeviceStatusResult
}

interface DeviceStatusGateway {
  fun fetch(
    deviceId: String,
    controlToken: String,
  ): DeviceStatusResult
}

class HttpDeviceStatusGateway : DeviceStatusGateway {
  override fun fetch(
    deviceId: String,
    controlToken: String,
  ): DeviceStatusResult {
    val connection =
      (URL(DEVICE_STATUS_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
      }

    return try {
      val body =
        JSONObject()
          .put("deviceId", deviceId)
          .put("controlToken", controlToken)
          .toString()

      connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(body)
      }

      val statusCode = connection.responseCode
      val responseBody =
        (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
          ?.bufferedReader(Charsets.UTF_8)
          ?.use { it.readText() }
          .orEmpty()

      if (statusCode in 200..299) {
        val json = JSONObject(responseBody)
        val deviceJson = json.getJSONObject("device")
        val state = DeviceAccessState.valueOf(deviceJson.getString("state"))
        val temporaryMinutes =
          if (
            state == DeviceAccessState.TEMPORARILY_ALLOWED &&
            !deviceJson.isNull("temporaryAccessMinutesRemaining")
          ) {
            deviceJson.getInt("temporaryAccessMinutesRemaining")
          } else {
            null
          }

        val protectionJson = deviceJson.optJSONObject("protection")

        DeviceStatusResult.Success(
          device =
            ChildDevice(
              deviceId = deviceJson.getString("deviceId"),
              displayName = deviceJson.getString("displayName"),
              state = state,
              temporaryAccessMinutesRemaining = temporaryMinutes,
              isOnline = deviceJson.optBoolean("isOnline", false),
              lastSeenAt =
                deviceJson.optString("lastSeenAt")
                  .takeIf { it.isNotBlank() && it != "null" },
              protectionStatus =
                DeviceProtectionStatus(
                  accessibilityEnabled =
                    protectionJson?.nullableBoolean("accessibilityEnabled"),
                  preciseTimingEnabled =
                    protectionJson?.nullableBoolean("preciseTimingEnabled"),
                  batteryUnrestricted =
                    protectionJson?.nullableBoolean("batteryUnrestricted"),
                ),
            ),
        )
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        DeviceStatusResult.Error(
          when (errorCode) {
            "device_auth_failed" ->
              "Parent pairing is no longer valid."
            else ->
              errorCode.ifBlank { "Backend error: HTTP " + statusCode }
          },
        )
      }
    } catch (error: Exception) {
      DeviceStatusResult.Error(
        error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val DEVICE_STATUS_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/device-status"
  }
}


private fun JSONObject.nullableBoolean(name: String): Boolean? =
  if (isNull(name)) null else getBoolean(name)
