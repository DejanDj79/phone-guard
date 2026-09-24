package com.example.phoneguard.parent.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface DailyLimitSaveResult {
  data class Success(
    val minutes: Int?,
    val version: Long,
  ) : DailyLimitSaveResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : DailyLimitSaveResult
}

interface DailyLimitGateway {
  fun save(
    deviceId: String,
    controlToken: String,
    minutes: Int?,
  ): DailyLimitSaveResult
}

class HttpDailyLimitGateway : DailyLimitGateway {
  override fun save(
    deviceId: String,
    controlToken: String,
    minutes: Int?,
  ): DailyLimitSaveResult {
    val connection =
      (URL(SET_DAILY_LIMIT_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
      }

    return try {
      val json =
        JSONObject()
          .put("deviceId", deviceId)
          .put("controlToken", controlToken)

      if (minutes == null) {
        json.put("dailyLimitMinutes", JSONObject.NULL)
      } else {
        json.put("dailyLimitMinutes", minutes)
      }

      connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(json.toString())
      }

      val statusCode = connection.responseCode
      val responseBody =
        (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
          ?.bufferedReader(Charsets.UTF_8)
          ?.use { it.readText() }
          .orEmpty()

      if (statusCode in 200..299) {
        val response = JSONObject(responseBody)
        DailyLimitSaveResult.Success(
          minutes =
            if (response.isNull("dailyLimitMinutes")) {
              null
            } else {
              response.getInt("dailyLimitMinutes")
            },
          version = response.optLong("dailyLimitVersion", 0L),
        )
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        DailyLimitSaveResult.Error(
          message =
            when (errorCode) {
              "device_auth_failed" ->
                "Parent pairing is no longer valid."
              "invalid_daily_limit" ->
                "Daily limit must be between 1 minute and 24 hours."
              "daily_limit_version_conflict" ->
                "Daily limit changed elsewhere. Please try again."
              else ->
                errorCode.ifBlank { "Backend error: HTTP " + statusCode }
            },
          pairingInvalid = errorCode == "device_auth_failed",
        )
      }
    } catch (error: Exception) {
      DailyLimitSaveResult.Error(
        message = error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val SET_DAILY_LIMIT_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/set-daily-limit"
  }
}
