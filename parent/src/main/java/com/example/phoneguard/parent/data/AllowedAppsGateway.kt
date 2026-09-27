package com.example.phoneguard.parent.data

import com.example.phoneguard.core.AllowedAppsSnapshot
import com.example.phoneguard.core.InstalledAppInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface AllowedAppsFetchResult {
  data class Success(
    val snapshot: AllowedAppsSnapshot,
  ) : AllowedAppsFetchResult

  data class Error(
    val message: String,
  ) : AllowedAppsFetchResult
}

sealed interface AllowedAppsSaveResult {
  data class Success(
    val version: Long,
  ) : AllowedAppsSaveResult

  data class Error(
    val message: String,
  ) : AllowedAppsSaveResult
}

interface AllowedAppsGateway {
  fun fetch(
    deviceId: String,
    controlToken: String,
  ): AllowedAppsFetchResult

  fun save(
    deviceId: String,
    controlToken: String,
    allowedPackages: Set<String>,
    expectedVersion: Long,
  ): AllowedAppsSaveResult
}

class HttpAllowedAppsGateway : AllowedAppsGateway {
  override fun fetch(
    deviceId: String,
    controlToken: String,
  ): AllowedAppsFetchResult {
    val connection =
      (URL(APPS_STATUS_URL).openConnection() as HttpURLConnection).apply {
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
        val apps = JSONObject(responseBody).getJSONObject("apps")
        val installedJson = apps.optJSONArray("installed")
        val installed =
          buildList {
            if (installedJson != null) {
              for (index in 0 until installedJson.length()) {
                val item = installedJson.optJSONObject(index) ?: continue
                val packageName = item.optString("packageName").trim()
                val label = item.optString("label").trim()
                val iconBase64 =
                  item.optString("iconBase64")
                    .trim()
                    .takeIf { it.isNotEmpty() && it != "null" }

                if (packageName.isNotEmpty() && label.isNotEmpty()) {
                  add(
                    InstalledAppInfo(
                      packageName = packageName,
                      label = label,
                      iconBase64 = iconBase64,
                    ),
                  )
                }
              }
            }
          }

        val allowedJson = apps.optJSONArray("allowedPackages")
        val allowed =
          buildSet {
            if (allowedJson != null) {
              for (index in 0 until allowedJson.length()) {
                val packageName = allowedJson.optString(index).trim()
                if (packageName.isNotEmpty()) add(packageName)
              }
            }
          }

        AllowedAppsFetchResult.Success(
          snapshot =
            AllowedAppsSnapshot(
              installedApps = installed,
              allowedPackages = allowed,
              version = apps.optLong("version", 0L),
            ),
        )
      } else {
        AllowedAppsFetchResult.Error(
          errorMessage(responseBody, statusCode),
        )
      }
    } catch (error: Exception) {
      AllowedAppsFetchResult.Error(
        error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  override fun save(
    deviceId: String,
    controlToken: String,
    allowedPackages: Set<String>,
    expectedVersion: Long,
  ): AllowedAppsSaveResult {
    val connection =
      (URL(SET_ALLOWED_APPS_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 15_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        applyParentAuthHeaders()
      }

    return try {
      val packagesJson = JSONArray()
      allowedPackages.sorted().forEach(packagesJson::put)

      val body =
        JSONObject()
          .put("deviceId", deviceId)
          .put("controlToken", controlToken)
          .put("allowedPackages", packagesJson)
          .put("expectedVersion", expectedVersion)
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
        AllowedAppsSaveResult.Success(
          version =
            JSONObject(responseBody)
              .optLong("allowedAppsVersion", expectedVersion + 1),
        )
      } else {
        AllowedAppsSaveResult.Error(
          errorMessage(responseBody, statusCode),
        )
      }
    } catch (error: Exception) {
      AllowedAppsSaveResult.Error(
        error.message ?: "Network error.",
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
      "invalid_allowed_apps" ->
        "Allowed apps selection is invalid."
      "allowed_apps_version_conflict" ->
        "Allowed apps changed in the meantime. Please reload and try again."
      "allowed_apps_saved_but_sync_queue_failed" ->
        "Allowed apps were saved, but Child synchronization is currently unavailable."
      else ->
        errorCode.ifBlank { "Backend error: HTTP " + statusCode }
    }
  }

  private companion object {
    const val APPS_STATUS_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/apps-status"
    const val SET_ALLOWED_APPS_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/set-allowed-apps"
  }
}
