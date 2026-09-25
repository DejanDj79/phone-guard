package com.example.phoneguard.parent.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUsageEntry(
  val packageName: String,
  val label: String,
  val seconds: Int,
)

data class AppUsageDay(
  val usageDate: String,
  val totalSeconds: Int,
  val apps: List<AppUsageEntry>,
  val updatedAt: String?,
)

sealed interface AppUsageResult {
  data class Success(
    val days: List<AppUsageDay>,
  ) : AppUsageResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : AppUsageResult
}

interface AppUsageGateway {
  fun fetch(
    deviceId: String,
    controlToken: String,
  ): AppUsageResult
}

class HttpAppUsageGateway : AppUsageGateway {
  override fun fetch(
    deviceId: String,
    controlToken: String,
  ): AppUsageResult {
    val connection =
      (URL(APP_USAGE_URL).openConnection() as HttpURLConnection).apply {
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
        (if (statusCode in 200..299) {
          connection.inputStream
        } else {
          connection.errorStream
        })
          ?.bufferedReader(Charsets.UTF_8)
          ?.use { it.readText() }
          .orEmpty()

      if (statusCode in 200..299) {
        val json = JSONObject(responseBody)
        val daysJson = json.optJSONArray("days")
        val days =
          buildList {
            if (daysJson != null) {
              for (dayIndex in 0 until daysJson.length()) {
                val dayJson = daysJson.optJSONObject(dayIndex) ?: continue
                val usageDate = dayJson.optString("usageDate").trim()
                if (usageDate.isBlank()) continue

                val appsJson = dayJson.optJSONArray("apps")
                val apps =
                  buildList {
                    if (appsJson != null) {
                      for (appIndex in 0 until appsJson.length()) {
                        val appJson =
                          appsJson.optJSONObject(appIndex) ?: continue
                        val packageName =
                          appJson.optString("packageName").trim()
                        val label = appJson.optString("label").trim()
                        val seconds =
                          appJson.optInt("seconds", 0).coerceAtLeast(0)

                        if (packageName.isNotBlank() && label.isNotBlank()) {
                          add(
                            AppUsageEntry(
                              packageName = packageName,
                              label = label,
                              seconds = seconds,
                            ),
                          )
                        }
                      }
                    }
                  }
                    .sortedByDescending { it.seconds }

                add(
                  AppUsageDay(
                    usageDate = usageDate,
                    totalSeconds =
                      dayJson.optInt("totalSeconds", 0).coerceAtLeast(0),
                    apps = apps,
                    updatedAt =
                      dayJson.optString("updatedAt")
                        .takeIf { it.isNotBlank() && it != "null" },
                  ),
                )
              }
            }
          }

        AppUsageResult.Success(days)
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        AppUsageResult.Error(
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
      AppUsageResult.Error(
        error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val APP_USAGE_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/app-usage"
  }
}
