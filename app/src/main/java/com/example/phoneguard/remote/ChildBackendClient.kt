package com.example.phoneguard.remote

import com.example.phoneguard.core.PairingIdentity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface ChildRegistrationResult {
  data class Success(
    val pairingExpiresAt: String,
    val paired: Boolean,
  ) : ChildRegistrationResult

  data class Failure(
    val message: String,
  ) : ChildRegistrationResult
}

data class PendingRemoteCommand(
  val commandId: String,
  val command: String,
  val bonusMinutes: Int?,
)

sealed interface ChildCommandSyncResult {
  data class Success(
    val commands: List<PendingRemoteCommand>,
  ) : ChildCommandSyncResult

  data class Failure(
    val message: String,
  ) : ChildCommandSyncResult
}

sealed interface ChildScheduleResult {
  data class Success(
    val config: String?,
    val version: Long,
  ) : ChildScheduleResult

  data class Failure(
    val message: String,
  ) : ChildScheduleResult
}

sealed interface ChildScheduleInitializeResult {
  data class Success(
    val version: Long,
  ) : ChildScheduleInitializeResult

  data class Failure(
    val message: String,
  ) : ChildScheduleInitializeResult
}

sealed interface ChildHeartbeatResult {
  data object Success : ChildHeartbeatResult

  data class Failure(
    val message: String,
  ) : ChildHeartbeatResult
}

sealed interface ChildCommandAckResult {
  data object Success : ChildCommandAckResult

  data class Failure(
    val message: String,
  ) : ChildCommandAckResult
}

sealed interface ChildPairingResetResult {
  data class Success(
    val pairingExpiresAt: String,
  ) : ChildPairingResetResult

  data class Failure(
    val message: String,
  ) : ChildPairingResetResult
}

class ChildBackendClient {
  fun register(
    identity: PairingIdentity,
    displayName: String,
    deviceSecret: String,
    fcmToken: String? = null,
  ): ChildRegistrationResult {
    val connection =
      (URL(REGISTER_CHILD_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
      }

    return try {
      val body =
        JSONObject()
          .put("deviceId", identity.deviceId)
          .put("displayName", displayName)
          .put("pairingCode", identity.pairingCode)
          .put("deviceSecret", deviceSecret)
          .also { json ->
            if (!fcmToken.isNullOrBlank()) {
              json.put("fcmToken", fcmToken)
            }
          }
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
        ChildRegistrationResult.Success(
          pairingExpiresAt = json.optString("pairingExpiresAt"),
          paired = json.optBoolean("paired", false),
        )
      } else {
        val error =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "HTTP " + statusCode }

        ChildRegistrationResult.Failure(error)
      }
    } catch (error: Exception) {
      ChildRegistrationResult.Failure(
        error.message ?: error::class.java.simpleName,
      )
    } finally {
      connection.disconnect()
    }
  }


  fun resetPairing(
    identity: PairingIdentity,
    deviceSecret: String,
  ): ChildPairingResetResult {
    val connection =
      (URL(RESET_PAIRING_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
      }

    return try {
      val body =
        JSONObject()
          .put("deviceId", identity.deviceId)
          .put("deviceSecret", deviceSecret)
          .put("pairingCode", identity.pairingCode)
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
        ChildPairingResetResult.Success(
          pairingExpiresAt = json.optString("pairingExpiresAt"),
        )
      } else {
        val error =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "HTTP " + statusCode }

        ChildPairingResetResult.Failure(
          when (error) {
            "device_auth_failed" -> "Child uređaj nije autorizovan."
            "pairing_code_collision" -> "Kod je već zauzet. Pokušaj ponovo."
            else -> error
          },
        )
      }
    } catch (error: Exception) {
      ChildPairingResetResult.Failure(
        error.message ?: error::class.java.simpleName,
      )
    } finally {
      connection.disconnect()
    }
  }

  fun acknowledgeCommand(
    deviceId: String,
    deviceSecret: String,
    commandId: String,
  ): ChildCommandAckResult {
    val connection =
      (URL(ACK_COMMAND_URL).openConnection() as HttpURLConnection).apply {
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
          .put("deviceSecret", deviceSecret)
          .put("commandId", commandId)
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
        ChildCommandAckResult.Success
      } else {
        val error =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "HTTP " + statusCode }

        ChildCommandAckResult.Failure(error)
      }
    } catch (error: Exception) {
      ChildCommandAckResult.Failure(
        error.message ?: error::class.java.simpleName,
      )
    } finally {
      connection.disconnect()
    }
  }

  fun syncCommands(
    deviceId: String,
    deviceSecret: String,
  ): ChildCommandSyncResult {
    val connection =
      (URL(SYNC_COMMANDS_URL).openConnection() as HttpURLConnection).apply {
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
          .put("deviceSecret", deviceSecret)
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
        val commandsJson = json.optJSONArray("commands")
        val commands =
          buildList {
            if (commandsJson != null) {
              for (index in 0 until commandsJson.length()) {
                val item = commandsJson.getJSONObject(index)
                add(
                  PendingRemoteCommand(
                    commandId = item.getString("commandId"),
                    command = item.getString("command"),
                    bonusMinutes =
                      if (item.isNull("bonusMinutes")) {
                        null
                      } else {
                        item.getInt("bonusMinutes")
                      },
                  ),
                )
              }
            }
          }

        ChildCommandSyncResult.Success(commands)
      } else {
        val error =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "HTTP " + statusCode }

        ChildCommandSyncResult.Failure(error)
      }
    } catch (error: Exception) {
      ChildCommandSyncResult.Failure(
        error.message ?: error::class.java.simpleName,
      )
    } finally {
      connection.disconnect()
    }
  }

  fun fetchSchedule(
    deviceId: String,
    deviceSecret: String,
  ): ChildScheduleResult {
    val connection =
      (URL(GET_SCHEDULE_URL).openConnection() as HttpURLConnection).apply {
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
          .put("deviceSecret", deviceSecret)
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
        val schedule = JSONObject(responseBody).getJSONObject("schedule")
        ChildScheduleResult.Success(
          config =
            if (schedule.isNull("config")) {
              null
            } else {
              schedule.getString("config")
            },
          version = schedule.optLong("version", 0L),
        )
      } else {
        val error =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "HTTP " + statusCode }

        ChildScheduleResult.Failure(error)
      }
    } catch (error: Exception) {
      ChildScheduleResult.Failure(
        error.message ?: error::class.java.simpleName,
      )
    } finally {
      connection.disconnect()
    }
  }

  fun initializeSchedule(
    deviceId: String,
    deviceSecret: String,
    scheduleConfig: String,
  ): ChildScheduleInitializeResult {
    val connection =
      (URL(INITIALIZE_SCHEDULE_URL).openConnection() as HttpURLConnection).apply {
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
          .put("deviceSecret", deviceSecret)
          .put("scheduleConfig", scheduleConfig)
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
        ChildScheduleInitializeResult.Success(
          version = json.optLong("version", 0L),
        )
      } else {
        val error =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "HTTP " + statusCode }

        ChildScheduleInitializeResult.Failure(error)
      }
    } catch (error: Exception) {
      ChildScheduleInitializeResult.Failure(
        error.message ?: error::class.java.simpleName,
      )
    } finally {
      connection.disconnect()
    }
  }

  fun heartbeat(
    deviceId: String,
    deviceSecret: String,
    accessState: String,
    temporaryAllowUntilMillis: Long?,
    accessibilityEnabled: Boolean,
    preciseTimingEnabled: Boolean,
    batteryUnrestricted: Boolean,
  ): ChildHeartbeatResult {
    val connection =
      (URL(HEARTBEAT_URL).openConnection() as HttpURLConnection).apply {
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
          .put("deviceSecret", deviceSecret)
          .put("accessState", accessState)
          .put("accessibilityEnabled", accessibilityEnabled)
          .put("preciseTimingEnabled", preciseTimingEnabled)
          .put("batteryUnrestricted", batteryUnrestricted)
          .also { json ->
            if (temporaryAllowUntilMillis != null) {
              json.put("temporaryAllowUntilMillis", temporaryAllowUntilMillis)
            }
          }
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
        ChildHeartbeatResult.Success
      } else {
        val error =
          runCatching { JSONObject(responseBody).optString("error") }
            .getOrDefault("")
            .ifBlank { "HTTP " + statusCode }

        ChildHeartbeatResult.Failure(error)
      }
    } catch (error: Exception) {
      ChildHeartbeatResult.Failure(
        error.message ?: error::class.java.simpleName,
      )
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val REGISTER_CHILD_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/register-child"
    const val RESET_PAIRING_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/reset-pairing"
    const val ACK_COMMAND_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/ack-command"
    const val SYNC_COMMANDS_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/sync-commands"
    const val HEARTBEAT_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/heartbeat"
    const val GET_SCHEDULE_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/get-schedule"
    const val INITIALIZE_SCHEDULE_URL =
      "https://lpcytegfsslhugeiefdu.supabase.co/functions/v1/initialize-schedule"
  }
}
