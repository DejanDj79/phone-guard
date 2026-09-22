package com.example.phoneguard.remote

import com.example.phoneguard.core.PairingIdentity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface ChildRegistrationResult {
  data class Success(
    val pairingExpiresAt: String,
    val paired: Boolean,
  ) : ChildRegistrationResult

  data class Failure(
    val message: String,
  ) : ChildRegistrationResult
}

sealed interface ChildCommandAckResult {
  data object Success : ChildCommandAckResult

  data class Failure(
    val message: String,
  ) : ChildCommandAckResult
}

sealed interface ChildPairingResetResult {
  data class Success(
    val pairingExpiresAt: String,
  ) : ChildPairingResetResult

  data class Failure(
    val message: String,
  ) : ChildPairingResetResult
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
          paired = json.optBoolean("paired", false),
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


  fun resetPairing(
    identity: PairingIdentity,
    deviceSecret: String,
  ): ChildPairingResetResult {
    val connection =
      (URL(RESET_PAIRING_URL).openConnection() as HttpURLConnection).apply {
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
          .put("deviceSecret", deviceSecret)
          .put("pairingCode", identity.pairingCode)
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
        ChildPairingResetResult.Success(
          pairingExpiresAt = json.optString("pairingExpiresAt"),
        )
      } else {
        val error =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "HTTP " + statusCode }

        ChildPairingResetResult.Failure(
          when (error) {
            "device_auth_failed" -> "Child uređaj nije autorizovan."
            "pairing_code_collision" -> "Kod je već zauzet. Pokušaj ponovo."
            else -> error
          },
        )
      }
    } catch (error: Exception) {
      ChildPairingResetResult.Failure(
        error.message ?: error::class.java.simpleName,
      )
    } finally {
      connection.disconnect()
    }
  }

  fun acknowledgeCommand(
    deviceId: String,
    deviceSecret: String,
    commandId: String,
  ): ChildCommandAckResult {
    val connection =
      (URL(ACK_COMMAND_URL).openConnection() as HttpURLConnection).apply {
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
          .put("deviceSecret", deviceSecret)
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
        ChildCommandAckResult.Success
      } else {
        val error =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "HTTP " + statusCode }

        ChildCommandAckResult.Failure(error)
      }
    } catch (error: Exception) {
      ChildCommandAckResult.Failure(
        error.message ?: error::class.java.simpleName,
      )
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val REGISTER_CHILD_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/register-child"
    const val RESET_PAIRING_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/reset-pairing"
    const val ACK_COMMAND_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/ack-command"
  }
}
