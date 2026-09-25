package com.example.phoneguard.parent.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PendingTimeRequest(
  val requestId: String,
  val deviceId: String,
  val displayName: String,
  val requestedMinutes: Int,
  val createdAt: String,
)

sealed interface TimeRequestFetchResult {
  data class Success(
    val request: PendingTimeRequest?,
  ) : TimeRequestFetchResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : TimeRequestFetchResult
}

sealed interface TimeRequestResponseResult {
  data class Success(
    val approved: Boolean,
    val requestedMinutes: Int,
  ) : TimeRequestResponseResult

  data class Error(
    val message: String,
    val pairingInvalid: Boolean = false,
  ) : TimeRequestResponseResult
}

interface TimeRequestGateway {
  fun fetch(
    deviceId: String,
    controlToken: String,
  ): TimeRequestFetchResult

  fun respond(
    deviceId: String,
    controlToken: String,
    requestId: String,
    approve: Boolean,
  ): TimeRequestResponseResult
}

class HttpTimeRequestGateway : TimeRequestGateway {
  override fun fetch(
    deviceId: String,
    controlToken: String,
  ): TimeRequestFetchResult {
    val response =
      post(
        url = TIME_REQUEST_STATUS_URL,
        body =
          JSONObject()
            .put("deviceId", deviceId)
            .put("controlToken", controlToken),
      )

    if (response.statusCode !in 200..299) {
      val error = errorCode(response.body)
      return TimeRequestFetchResult.Error(
        message = errorMessage(error, response.statusCode),
        pairingInvalid = error == "device_auth_failed",
      )
    }

    return runCatching {
      val json = JSONObject(response.body)
      val requestJson =
        if (json.isNull("request")) {
          null
        } else {
          json.getJSONObject("request")
        }

      TimeRequestFetchResult.Success(
        request =
          requestJson?.let {
            PendingTimeRequest(
              requestId = it.getString("requestId"),
              deviceId = it.getString("deviceId"),
              displayName = it.getString("displayName"),
              requestedMinutes = it.getInt("requestedMinutes"),
              createdAt = it.getString("createdAt"),
            )
          },
      )
    }.getOrElse {
      TimeRequestFetchResult.Error("Invalid backend response.")
    }
  }

  override fun respond(
    deviceId: String,
    controlToken: String,
    requestId: String,
    approve: Boolean,
  ): TimeRequestResponseResult {
    val response =
      post(
        url = RESPOND_TIME_REQUEST_URL,
        body =
          JSONObject()
            .put("deviceId", deviceId)
            .put("controlToken", controlToken)
            .put("requestId", requestId)
            .put("decision", if (approve) "APPROVE" else "DENY"),
      )

    if (response.statusCode !in 200..299) {
      val error = errorCode(response.body)
      return TimeRequestResponseResult.Error(
        message = errorMessage(error, response.statusCode),
        pairingInvalid = error == "device_auth_failed",
      )
    }

    return runCatching {
      val json = JSONObject(response.body)
      TimeRequestResponseResult.Success(
        approved = json.optString("decision") == "APPROVED",
        requestedMinutes = json.optInt("requestedMinutes", 0),
      )
    }.getOrElse {
      TimeRequestResponseResult.Error("Invalid backend response.")
    }
  }

  private fun post(
    url: String,
    body: JSONObject,
  ): HttpResponse {
    val connection =
      (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 15_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
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

      HttpResponse(statusCode, responseBody)
    } catch (error: Exception) {
      HttpResponse(
        statusCode = 0,
        body =
          JSONObject()
            .put("error", error.message ?: "Network error.")
            .toString(),
      )
    } finally {
      connection.disconnect()
    }
  }

  private fun errorCode(body: String): String =
    runCatching { JSONObject(body).optString("error") }
      .getOrDefault("")

  private fun errorMessage(
    code: String,
    statusCode: Int,
  ): String =
    when (code) {
      "device_auth_failed" ->
        "Parent pairing is no longer valid."
      "request_not_found" ->
        "This time request no longer exists."
      "request_already_resolved" ->
        "This time request was already answered."
      "device_has_no_fcm_token" ->
        "Child device is not ready to receive remote commands."
      "fcm_send_failed" ->
        "The approval could not be delivered to the Child device."
      else ->
        code.ifBlank {
          if (statusCode == 0) "Network error."
          else "Backend error: HTTP " + statusCode
        }
    }

  private data class HttpResponse(
    val statusCode: Int,
    val body: String,
  )

  private companion object {
    const val TIME_REQUEST_STATUS_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/time-request-status"
    const val RESPOND_TIME_REQUEST_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/respond-time-request"
  }
}
