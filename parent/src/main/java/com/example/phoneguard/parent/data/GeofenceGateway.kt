package com.example.phoneguard.parent.data

import com.example.phoneguard.core.GeofenceZone
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface GeofenceZonesResult {
  data class Success(
    val zones: List<GeofenceZone>,
  ) : GeofenceZonesResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : GeofenceZonesResult
}

sealed interface GeofenceSaveResult {
  data class Success(
    val zone: GeofenceZone,
  ) : GeofenceSaveResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : GeofenceSaveResult
}

sealed interface GeofenceDeleteResult {
  data object Success : GeofenceDeleteResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : GeofenceDeleteResult
}

class HttpGeofenceGateway {
  fun fetch(
    deviceId: String,
    controlToken: String,
  ): GeofenceZonesResult {
    val result =
      request(
        JSONObject()
          .put("deviceId", deviceId)
          .put("controlToken", controlToken)
          .put("action", "list"),
      )

    return when (result) {
      is RawResult.Success -> {
        val zonesJson = result.json.optJSONArray("zones")
        val zones =
          buildList {
            if (zonesJson != null) {
              for (index in 0 until zonesJson.length()) {
                add(parseZone(zonesJson.getJSONObject(index)))
              }
            }
          }
        GeofenceZonesResult.Success(zones)
      }

      is RawResult.Error ->
        GeofenceZonesResult.Error(
          message = result.message,
          pairingInvalid = result.pairingInvalid,
        )
    }
  }

  fun save(
    deviceId: String,
    controlToken: String,
    name: String,
    latitude: Double,
    longitude: Double,
    radiusMeters: Int,
    zoneId: String? = null,
    enabled: Boolean = true,
  ): GeofenceSaveResult {
    val body =
      JSONObject()
        .put("deviceId", deviceId)
        .put("controlToken", controlToken)
        .put("action", "save")
        .put("name", name)
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("radiusMeters", radiusMeters)
        .put("enabled", enabled)
        .also { json ->
          zoneId?.takeIf(String::isNotBlank)?.let {
            json.put("zoneId", it)
          }
        }

    return when (val result = request(body)) {
      is RawResult.Success ->
        GeofenceSaveResult.Success(
          parseZone(result.json.getJSONObject("zone")),
        )

      is RawResult.Error ->
        GeofenceSaveResult.Error(
          message = result.message,
          pairingInvalid = result.pairingInvalid,
        )
    }
  }

  fun delete(
    deviceId: String,
    controlToken: String,
    zoneId: String,
  ): GeofenceDeleteResult {
    val result =
      request(
        JSONObject()
          .put("deviceId", deviceId)
          .put("controlToken", controlToken)
          .put("action", "delete")
          .put("zoneId", zoneId),
      )

    return when (result) {
      is RawResult.Success -> GeofenceDeleteResult.Success
      is RawResult.Error ->
        GeofenceDeleteResult.Error(
          message = result.message,
          pairingInvalid = result.pairingInvalid,
        )
    }
  }

  private fun request(body: JSONObject): RawResult {
    val connection =
      (URL(GEOFENCE_ZONES_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        applyParentAuthHeaders()
      }

    return try {
      connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(body.toString())
      }

      val statusCode = connection.responseCode
      val responseBody =
        (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
          ?.bufferedReader(Charsets.UTF_8)
          ?.use { it.readText() }
          .orEmpty()

      if (statusCode in 200..299) {
        RawResult.Success(JSONObject(responseBody))
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        RawResult.Error(
          message =
            when (errorCode) {
              "device_auth_failed" ->
                "Parent pairing is no longer valid."
              "zone_limit_reached" ->
                "This Child device already has the maximum number of location zones."
              "invalid_radius" ->
                "Zone radius must be between 50 and 5000 meters."
              "zone_not_found" ->
                "Location zone was not found."
              else ->
                errorCode.ifBlank { "Backend error: HTTP " + statusCode }
            },
          pairingInvalid = errorCode == "device_auth_failed",
        )
      }
    } catch (error: Exception) {
      RawResult.Error(
        message = error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }

  private fun parseZone(json: JSONObject): GeofenceZone =
    GeofenceZone(
      zoneId = json.getString("zoneId"),
      name = json.getString("name"),
      latitude = json.getDouble("latitude"),
      longitude = json.getDouble("longitude"),
      radiusMeters = json.getInt("radiusMeters"),
      enabled = json.optBoolean("enabled", true),
    )

  private sealed interface RawResult {
    data class Success(
      val json: JSONObject,
    ) : RawResult

    data class Error(
      val message: String,
      val pairingInvalid: Boolean = false,
    ) : RawResult
  }

  private companion object {
    const val GEOFENCE_ZONES_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/geofence-zones"
  }
}
