package com.example.phoneguard.parent.data

import com.example.phoneguard.core.RemoteWeeklySchedule
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface ScheduleFetchResult {
  data class Success(
    val schedule: RemoteWeeklySchedule,
    val version: Long,
  ) : ScheduleFetchResult

  data class Error(
    val message: String,
  ) : ScheduleFetchResult
}

sealed interface ScheduleSaveResult {
  data class Success(
    val version: Long,
  ) : ScheduleSaveResult

  data class Error(
    val message: String,
  ) : ScheduleSaveResult
}

interface ScheduleGateway {
  fun fetch(
    deviceId: String,
    controlToken: String,
  ): ScheduleFetchResult

  fun save(
    deviceId: String,
    controlToken: String,
    schedule: RemoteWeeklySchedule,
  ): ScheduleSaveResult
}

class HttpScheduleGateway : ScheduleGateway {
  override fun fetch(
    deviceId: String,
    controlToken: String,
  ): ScheduleFetchResult {
    val connection =
      (URL(SCHEDULE_STATUS_URL).openConnection() as HttpURLConnection).apply {
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
        val scheduleJson = JSONObject(responseBody).getJSONObject("schedule")
        val config =
          if (scheduleJson.isNull("config")) {
            null
          } else {
            scheduleJson.getString("config")
          }

        ScheduleFetchResult.Success(
          schedule = RemoteWeeklySchedule.decode(config),
          version = scheduleJson.optLong("version", 0L),
        )
      } else {
        ScheduleFetchResult.Error(errorMessage(responseBody, statusCode))
      }
    } catch (error: Exception) {
      ScheduleFetchResult.Error(error.message ?: "Mrežna greška.")
    } finally {
      connection.disconnect()
    }
  }

  override fun save(
    deviceId: String,
    controlToken: String,
    schedule: RemoteWeeklySchedule,
  ): ScheduleSaveResult {
    val connection =
      (URL(SET_SCHEDULE_URL).openConnection() as HttpURLConnection).apply {
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
          .put("scheduleConfig", schedule.encode())
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
        ScheduleSaveResult.Success(
          version = json.optLong("scheduleVersion", 0L),
        )
      } else {
        ScheduleSaveResult.Error(errorMessage(responseBody, statusCode))
      }
    } catch (error: Exception) {
      ScheduleSaveResult.Error(error.message ?: "Mrežna greška.")
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
        "Parent pairing više nije važeći."
      "invalid_schedule" ->
        "Raspored nije ispravan."
      "schedule_version_conflict" ->
        "Raspored je u međuvremenu promenjen. Pokušaj ponovo."
      "schedule_saved_but_sync_queue_failed" ->
        "Raspored je sačuvan, ali Child sinhronizacija trenutno nije dostupna."
      else ->
        errorCode.ifBlank { "Backend greška: HTTP " + statusCode }
    }
  }

  private companion object {
    const val SCHEDULE_STATUS_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/schedule-status"
    const val SET_SCHEDULE_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/set-schedule"
  }
}
