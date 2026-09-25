package com.example.phoneguard.parent.data

import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.PairingRequest
import com.example.phoneguard.core.PairingResult
import com.example.phoneguard.parent.auth.ParentSupabase
import io.github.jan.supabase.auth.auth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface PairingGateway {
  fun pair(request: PairingRequest): PairingResult
}

class HttpPairingGateway(
  private val accessTokenProvider: () -> String? = {
    ParentSupabase.client.auth.currentSessionOrNull()?.accessToken
  },
) : PairingGateway {
  override fun pair(request: PairingRequest): PairingResult {
    val accessToken =
      accessTokenProvider()
        ?.takeIf { it.isNotBlank() }
        ?: return PairingResult.Error(
          "Sign in to your Parent account before pairing a Child device.",
        )

    val connection =
      (URL(ParentSupabase.functionUrl("pair-child")).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("apikey", ParentSupabase.SUPABASE_PUBLISHABLE_KEY)
        setRequestProperty("Authorization", "Bearer $accessToken")
      }

    return try {
      val body =
        JSONObject()
          .put("pairingCode", request.pairingCode)
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
        val deviceJson = json.getJSONObject("device")
        val state = DeviceAccessState.valueOf(deviceJson.getString("state"))
        val temporaryMinutes =
          if (
            state == DeviceAccessState.TEMPORARILY_ALLOWED &&
            !deviceJson.isNull("temporaryAccessMinutesRemaining")
          ) {
            deviceJson.getInt("temporaryAccessMinutesRemaining")
          } else {
            null
          }

        PairingResult.Success(
          device =
            ChildDevice(
              deviceId = deviceJson.getString("deviceId"),
              displayName = deviceJson.getString("displayName"),
              state = state,
              temporaryAccessMinutesRemaining = temporaryMinutes,
            ),
          controlToken = json.getString("controlToken"),
        )
      } else {
        val errorCode =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")

        when {
          statusCode == 404 || errorCode == "invalid_or_expired_code" ->
            PairingResult.InvalidCode("Code is invalid or has expired.")

          statusCode == 401 ->
            PairingResult.Error(
              "Your Parent account session is no longer valid. Sign in again and retry.",
            )

          statusCode == 409 && errorCode == "device_owned_by_another_parent" ->
            PairingResult.Error(
              "This Child device is already linked to another Parent account.",
            )

          statusCode == 409 ->
            PairingResult.Error(
              "Pairing changed while the request was being processed. Generate a new Child code and retry.",
            )

          statusCode == 429 ->
            PairingResult.Error("Too many attempts. Please wait a few minutes.")

          else ->
            PairingResult.Error(
              errorCode.ifBlank { "Backend error: HTTP " + statusCode },
            )
        }
      }
    } catch (error: Exception) {
      PairingResult.Error(
        error.message ?: "Network error.",
      )
    } finally {
      connection.disconnect()
    }
  }
}
