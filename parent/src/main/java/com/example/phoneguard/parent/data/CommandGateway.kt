package com.example.phoneguard.parent.data

import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.DeviceProtectionStatus
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.core.RemoteCommandType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface CommandResult {
  data class Success(
    val device: ChildDevice,
    val commandId: String,
    val deliveryStatus: String,
  ) : CommandResult

  data class Error(
    val message: String,
  ) : CommandResult
}

sealed interface CommandDeliveryResult {
  data class Success(
    val status: String,
    val device: ChildDevice,
  ) : CommandDeliveryResult

  data class Error(
    val message: String,
  ) : CommandDeliveryResult
}

interface CommandGateway {
  fun send(
    deviceId: String,
    controlToken: String,
    command: RemoteCommand,
  ): CommandResult

  fun status(
    deviceId: String,
    controlToken: String,
    commandId: String,
  ): CommandDeliveryResult
}

class HttpCommandGateway : CommandGateway {
  override fun send(
    deviceId: String,
    controlToken: String,
    command: RemoteCommand,
  ): CommandResult {
    val connection =
      (URL(SEND_COMMAND_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 15_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
      }

    return try {
      val body =
        JSONObject()
          .put("deviceId", deviceId)
          .put("controlToken", controlToken)
          .put("command", command.type.name)
          .also { json ->
            if (command.type == RemoteCommandType.BONUS_TIME) {
              json.put("bonusMinutes", command.bonusMinutes)
            }
          }
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

        CommandResult.Success(
          device =
            ChildDevice(
              deviceId = deviceJson.getString("deviceId"),
              displayName = deviceJson.getString("displayName"),
              state = state,
              temporaryAccessMinutesRemaining = temporaryMinutes,
              protectionStatus = deviceJson.protectionStatus(),
            ),
          commandId = json.getString("commandId"),
          deliveryStatus = json.optString("deliveryStatus", "SENT"),
        )
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        CommandResult.Error(
          when (errorCode) {
            "device_auth_failed" ->
              "Parent pairing is no longer valid."
            "device_has_no_fcm_token" ->
              "Child device does not have an FCM token yet."
            "firebase_not_configured" ->
              "Firebase server credentials are not configured yet."
            "firebase_oauth_failed" ->
              "Firebase authorization failed."
            "fcm_send_failed" ->
              "FCM did not accept the message."
            else ->
              errorCode.ifBlank { "Backend error: HTTP " + statusCode }
          },
        )
      }
    } catch (error: Exception) {
      CommandResult.Error(
        error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  override fun status(
    deviceId: String,
    controlToken: String,
    commandId: String,
  ): CommandDeliveryResult {
    val connection =
      (URL(COMMAND_STATUS_URL).openConnection() as HttpURLConnection).apply {
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
          .put("commandId", commandId)
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

        CommandDeliveryResult.Success(
          status = json.optString("status", "SENT"),
          device =
            ChildDevice(
              deviceId = deviceJson.getString("deviceId"),
              displayName = deviceJson.getString("displayName"),
              state = state,
              temporaryAccessMinutesRemaining = temporaryMinutes,
              protectionStatus = deviceJson.protectionStatus(),
            ),
        )
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        CommandDeliveryResult.Error(
          when (errorCode) {
            "device_auth_failed" ->
              "Parent pairing is no longer valid."
            "command_not_found" ->
              "Command was not found on the backend."
            else ->
              errorCode.ifBlank { "Backend error: HTTP " + statusCode }
          },
        )
      }
    } catch (error: Exception) {
      CommandDeliveryResult.Error(
        error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val SEND_COMMAND_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/send-command"
    const val COMMAND_STATUS_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/command-status"
  }
}


private fun JSONObject.protectionStatus(): DeviceProtectionStatus {
  val protection = optJSONObject("protection")
    ?: return DeviceProtectionStatus()

  return DeviceProtectionStatus(
    accessibilityEnabled =
      protection.nullableBoolean("accessibilityEnabled"),
    preciseTimingEnabled =
      protection.nullableBoolean("preciseTimingEnabled"),
    batteryUnrestricted =
      protection.nullableBoolean("batteryUnrestricted"),
  )
}

private fun JSONObject.nullableBoolean(name: String): Boolean? =
  if (isNull(name)) null else getBoolean(name)
