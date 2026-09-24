package com.example.phoneguard.parent.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class DevicePresenceState {
  ONLINE,
  LAST_SEEN,
  POSSIBLE_SHUTDOWN,
  UNKNOWN,
}

internal fun devicePresenceState(
  lastSeenAt: String?,
  nowMillis: Long = System.currentTimeMillis(),
): DevicePresenceState {
  val lastSeenMillis = parseLastSeenMillis(lastSeenAt)
    ?: return DevicePresenceState.UNKNOWN
  val ageMillis = (nowMillis - lastSeenMillis).coerceAtLeast(0L)

  return when {
    ageMillis <= ONLINE_RECENT_MS ->
      DevicePresenceState.ONLINE
    ageMillis >= POSSIBLE_SHUTDOWN_AFTER_MS ->
      DevicePresenceState.POSSIBLE_SHUTDOWN
    else ->
      DevicePresenceState.LAST_SEEN
  }
}

internal fun devicePresenceSummary(lastSeenAt: String?): String =
  when (devicePresenceState(lastSeenAt)) {
    DevicePresenceState.ONLINE ->
      "● Online"
    DevicePresenceState.LAST_SEEN ->
      formatLastSeen(lastSeenAt)
    DevicePresenceState.POSSIBLE_SHUTDOWN ->
      "⚠ Possible shutdown · " + formatLastSeen(lastSeenAt)
    DevicePresenceState.UNKNOWN ->
      "Last seen: unknown"
  }

internal fun formatLastSeen(value: String?): String {
  val date = parseLastSeenMillis(value)?.let(::Date)
    ?: return "Last seen: unknown"
  val formatter =
    SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

  return "Last seen: " + formatter.format(date)
}

private fun parseLastSeenMillis(value: String?): Long? {
  if (value.isNullOrBlank()) return null

  val normalized =
    value.replace(
      Regex("(\\.\\d{3})\\d+"),
      "\$1",
    )
  val patterns =
    listOf(
      "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
      "yyyy-MM-dd'T'HH:mm:ssXXX",
    )

  for (pattern in patterns) {
    val parsed =
      runCatching {
        SimpleDateFormat(pattern, Locale.US).parse(normalized)
      }.getOrNull()

    if (parsed != null) return parsed.time
  }

  return null
}

private const val ONLINE_RECENT_MS = 2 * 60_000L
private const val POSSIBLE_SHUTDOWN_AFTER_MS = 30 * 60_000L
