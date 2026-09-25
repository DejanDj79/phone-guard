package com.example.phoneguard.parent.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface RenameDeviceResult {
  data class Success(
    val displayName: String,
  ) : RenameDeviceResult

  data class Error(
    val message: String,
  ) : RenameDeviceResult
}

sealed interface UnpairDeviceResult {
  data object Success : UnpairDeviceResult

  data class Error(
    val message: String,
  ) : UnpairDeviceResult
}

interface DeviceManagementGateway {
  fun rename(
    deviceId: String,
    controlToken: String,
    displayName: String,
  ): RenameDeviceResult

  fun unpair(
    deviceId: String,
    controlToken: String,
  ): UnpairDeviceResult
}

class HttpDeviceManagementGateway : DeviceManagementGateway {
  override fun rename(
    deviceId: String,
    controlToken: String,
    displayName: String,
  ): RenameDeviceResult {
    val response =
      post(
        url = RENAME_DEVICE_URL,
        body =
          JSONObject()
            .put("deviceId", deviceId)
            .put("controlToken", controlToken)
            .put("displayName", displayName),
      )

    return if (response.statusCode in 200..299) {
      val json = JSONObject(response.body)
      RenameDeviceResult.Success(
        displayName = json.getString("displayName"),
      )
    } else {
      RenameDeviceResult.Error(
        errorMessage(response.body, response.statusCode),
      )
    }
  }

  override fun unpair(
    deviceId: String,
    controlToken: String,
  ): UnpairDeviceResult {
    val response =
      post(
        url = UNPAIR_DEVICE_URL,
        body =
          JSONObject()
            .put("deviceId", deviceId)
            .put("controlToken", controlToken),
      )

    return if (response.statusCode in 200..299) {
      UnpairDeviceResult.Success
    } else {
      UnpairDeviceResult.Error(
        errorMessage(response.body, response.statusCode),
      )
    }
  }

  private fun post(
    url: String,
    body: JSONObject,
  ): HttpResponse {
    val connection =
      (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        applyParentAuthHeaders()
      }

    return try {
      connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(body.toString())
      }

      val statusCode = connection.responseCode
      val responseBody =
        (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
          ?.bufferedReader(Charsets.UTF_8)
          ?.use { it.readText() }
          .orEmpty()

      HttpResponse(statusCode, responseBody)
    } catch (error: Exception) {
      HttpResponse(
        statusCode = 0,
        body =
          JSONObject()
            .put("error", error.message ?: "Network error.")
            .toString(),
      )
    } finally {
      connection.disconnect()
    }
  }

  private fun errorMessage(
    responseBody: String,
    statusCode: Int,
  ): String {
    val errorCode =
      runCatching { JSONObject(responseBody).optString("error") }
        .getOrDefault("")

    return when (errorCode) {
      "device_auth_failed" ->
        "Parent pairing is no longer valid."
      "invalid_display_name" ->
        "Device name must contain 1 to 40 characters."
      "command_cleanup_failed" ->
        "Pairing could not be safely removed. Please try again."
      else ->
        errorCode.ifBlank {
          if (statusCode == 0) {
            "Network error."
          } else {
            "Backend error: HTTP " + statusCode
          }
        }
    }
  }

  private data class HttpResponse(
    val statusCode: Int,
    val body: String,
  )

  private companion object {
    const val RENAME_DEVICE_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/rename-device"
    const val UNPAIR_DEVICE_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/unpair-device"
  }
}
