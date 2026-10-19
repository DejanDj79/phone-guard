package com.example.phoneguard.parent.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChildLocation(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Double?,
  val capturedAt: String,
  val receivedAt: String?,
)

sealed interface LocationStatusResult {
  data class Success(
    val location: ChildLocation?,
  ) : LocationStatusResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : LocationStatusResult
}

interface LocationGateway {
  fun fetch(
    deviceId: String,
    controlToken: String,
  ): LocationStatusResult
}

class HttpLocationGateway : LocationGateway {
  override fun fetch(
    deviceId: String,
    controlToken: String,
  ): LocationStatusResult {
    val connection =
      (URL(LOCATION_STATUS_URL).openConnection() as HttpURLConnection).apply {
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
        val locationJson =
          if (json.isNull("location")) {
            null
          } else {
            json.getJSONObject("location")
          }

        LocationStatusResult.Success(
          location =
            locationJson?.let {
              ChildLocation(
                latitude = it.getDouble("latitude"),
                longitude = it.getDouble("longitude"),
                accuracyMeters =
                  if (it.isNull("accuracyMeters")) {
                    null
                  } else {
                    it.getDouble("accuracyMeters")
                  },
                capturedAt = it.getString("capturedAt"),
                receivedAt =
                  it.optString("receivedAt")
                    .takeIf { value -> value.isNotBlank() && value != "null" },
              )
            },
        )
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        LocationStatusResult.Error(
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
      LocationStatusResult.Error(
        error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val LOCATION_STATUS_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/location-status"
  }
}
