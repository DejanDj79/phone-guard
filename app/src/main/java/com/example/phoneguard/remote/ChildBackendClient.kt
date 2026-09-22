package com.example.phoneguard.remote

import com.example.phoneguard.core.PairingIdentity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface ChildRegistrationResult {
  data class Success(
    val pairingExpiresAt: String,
  ) : ChildRegistrationResult

  data class Failure(
    val message: String,
  ) : ChildRegistrationResult
}

class ChildBackendClient {
  fun register(
    identity: PairingIdentity,
    displayName: String,
    deviceSecret: String,
    fcmToken: String? = null,
  ): ChildRegistrationResult {
    val connection =
      (URL(REGISTER_CHILD_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
      }

    return try {
      val body =
        JSONObject()
          .put("deviceId", identity.deviceId)
          .put("displayName", displayName)
          .put("pairingCode", identity.pairingCode)
          .put("deviceSecret", deviceSecret)
          .also { json ->
            if (!fcmToken.isNullOrBlank()) {
              json.put("fcmToken", fcmToken)
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
        ChildRegistrationResult.Success(
          pairingExpiresAt = json.optString("pairingExpiresAt"),
        )
      } else {
        val error =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "HTTP " + statusCode }

        ChildRegistrationResult.Failure(error)
      }
    } catch (error: Exception) {
      ChildRegistrationResult.Failure(
        error.message ?: error::class.java.simpleName,
      )
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val REGISTER_CHILD_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/register-child"
  }
}
