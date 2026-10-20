package com.example.phoneguard.parent.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GeofenceEvent(
  val eventId: String,
  val zoneId: String?,
  val zoneName: String,
  val eventType: String,
  val occurredAt: String,
  val receivedAt: String,
)

sealed interface GeofenceEventsResult {
  data class Success(
    val events: List<GeofenceEvent>,
  ) : GeofenceEventsResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : GeofenceEventsResult
}

class HttpGeofenceEventGateway {
  fun fetch(
    deviceId: String,
    controlToken: String,
  ): GeofenceEventsResult {
    val connection =
      (URL(GEOFENCE_EVENTS_URL).openConnection() as HttpURLConnection).apply {
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
                val eventId = event.optString("eventId").trim()
                val zoneName = event.optString("zoneName").trim()
                val eventType = event.optString("eventType").trim().uppercase()
                val occurredAt = event.optString("occurredAt").trim()
                val receivedAt = event.optString("receivedAt").trim()

                if (
                  eventId.isNotBlank() &&
                  zoneName.isNotBlank() &&
                  (eventType == "ENTER" || eventType == "EXIT") &&
                  occurredAt.isNotBlank()
                ) {
                  add(
                    GeofenceEvent(
                      eventId = eventId,
                      zoneId =
                        event.optString("zoneId")
                          .trim()
                          .takeIf(String::isNotBlank),
                      zoneName = zoneName,
                      eventType = eventType,
                      occurredAt = occurredAt,
                      receivedAt = receivedAt,
                    ),
                  )
                }
              }
            }
          }

        GeofenceEventsResult.Success(events)
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        GeofenceEventsResult.Error(
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
      GeofenceEventsResult.Error(
        error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val GEOFENCE_EVENTS_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/geofence-events"
  }
}
