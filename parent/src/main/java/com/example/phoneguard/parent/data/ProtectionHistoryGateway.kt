package com.example.phoneguard.parent.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ProtectionHistoryEvent(
  val eventType: String,
  val createdAt: String,
)

sealed interface ProtectionHistoryClearResult {
  data object Success : ProtectionHistoryClearResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : ProtectionHistoryClearResult
}

sealed interface ProtectionHistoryResult {
  data class Success(
    val events: List<ProtectionHistoryEvent>,
  ) : ProtectionHistoryResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : ProtectionHistoryResult
}

interface ProtectionHistoryGateway {
  fun fetch(
    deviceId: String,
    controlToken: String,
  ): ProtectionHistoryResult

  fun clear(
    deviceId: String,
    controlToken: String,
  ): ProtectionHistoryClearResult
}

class HttpProtectionHistoryGateway : ProtectionHistoryGateway {
  override fun fetch(
    deviceId: String,
    controlToken: String,
  ): ProtectionHistoryResult {
    val connection =
      (URL(PROTECTION_HISTORY_URL).openConnection() as HttpURLConnection).apply {
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
        val eventsJson = json.optJSONArray("events")
        val events =
          buildList {
            if (eventsJson != null) {
              for (index in 0 until eventsJson.length()) {
                val event = eventsJson.optJSONObject(index) ?: continue
                val eventType = event.optString("eventType").trim()
                val createdAt = event.optString("createdAt").trim()
                if (eventType.isNotBlank() && createdAt.isNotBlank()) {
                  add(
                    ProtectionHistoryEvent(
                      eventType = eventType,
                      createdAt = createdAt,
                    ),
                  )
                }
              }
            }
          }

        ProtectionHistoryResult.Success(events)
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        ProtectionHistoryResult.Error(
          message =
            when (errorCode) {
              "device_auth_failed" ->
                "Parent pairing is no longer valid."
              else ->
                errorCode.ifBlank { "Backend error: HTTP " + statusCode }
            },
          pairingInvalid = errorCode == "device_auth_failed",
        )
      }
    } catch (error: Exception) {
      ProtectionHistoryResult.Error(
        error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  override fun clear(
    deviceId: String,
    controlToken: String,
  ): ProtectionHistoryClearResult {
    val connection =
      (URL(PROTECTION_HISTORY_URL).openConnection() as HttpURLConnection).apply {
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
          .put("action", "clear")
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
        ProtectionHistoryClearResult.Success
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        ProtectionHistoryClearResult.Error(
          message =
            when (errorCode) {
              "device_auth_failed" ->
                "Parent pairing is no longer valid."
              else ->
                errorCode.ifBlank { "Backend error: HTTP " + statusCode }
            },
          pairingInvalid = errorCode == "device_auth_failed",
        )
      }
    } catch (error: Exception) {
      ProtectionHistoryClearResult.Error(
        error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val PROTECTION_HISTORY_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/protection-history"
  }
}
