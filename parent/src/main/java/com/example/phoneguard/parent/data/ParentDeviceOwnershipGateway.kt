package com.example.phoneguard.parent.data

import com.example.phoneguard.parent.auth.ParentSupabase
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface ParentDeviceClaimResult {
  data object Success : ParentDeviceClaimResult

  data class Error(
    val code: String,
  ) : ParentDeviceClaimResult
}

interface ParentDeviceOwnershipGateway {
  fun claim(
    deviceId: String,
    controlToken: String,
    accessToken: String,
  ): ParentDeviceClaimResult
}

class HttpParentDeviceOwnershipGateway : ParentDeviceOwnershipGateway {
  override fun claim(
    deviceId: String,
    controlToken: String,
    accessToken: String,
  ): ParentDeviceClaimResult {
    if (deviceId.isBlank() || controlToken.isBlank() || accessToken.isBlank()) {
      return ParentDeviceClaimResult.Error("invalid_local_pairing")
    }

    val connection =
      (URL(ParentSupabase.functionUrl("claim-parent-device"))
        .openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 5_000
        readTimeout = 5_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("apikey", ParentSupabase.SUPABASE_PUBLISHABLE_KEY)
        setRequestProperty("Authorization", "Bearer $accessToken")
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
        ParentDeviceClaimResult.Success
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "http_$statusCode" }

        ParentDeviceClaimResult.Error(errorCode)
      }
    } catch (error: Exception) {
      ParentDeviceClaimResult.Error(
        error.message ?: "network_error",
      )
    } finally {
      connection.disconnect()
    }
  }
}
