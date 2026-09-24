package com.example.phoneguard.core

enum class ScheduleDay(
  val displayName: String,
) {
  MONDAY("Monday"),
  TUESDAY("Tuesday"),
  WEDNESDAY("Wednesday"),
  THURSDAY("Thursday"),
  FRIDAY("Friday"),
  SATURDAY("Saturday"),
  SUNDAY("Sunday"),
}

data class RemoteDaySchedule(
  val enabled: Boolean = false,
  val startMinutes: Int = 22 * 60,
  val endMinutes: Int = 7 * 60,
) {
  init {
    require(startMinutes in 0..1439)
    require(endMinutes in 0..1439)
  }

  val startLabel: String
    get() = startMinutes.toTimeLabel()

  val endLabel: String
    get() = endMinutes.toTimeLabel()
}

data class RemoteWeeklySchedule(
  val days: Map<ScheduleDay, RemoteDaySchedule> =
    ScheduleDay.entries.associateWith { RemoteDaySchedule() },
) {
  fun scheduleFor(day: ScheduleDay): RemoteDaySchedule =
    days[day] ?: RemoteDaySchedule()

  fun enabledDaysCount(): Int =
    ScheduleDay.entries.count { scheduleFor(it).enabled }

  fun encode(): String =
    ScheduleDay.entries.joinToString(";") { day ->
      val schedule = scheduleFor(day)
      listOf(
        day.name,
        if (schedule.enabled) "1" else "0",
        schedule.startMinutes.toString(),
        schedule.endMinutes.toString(),
      ).joinToString(",")
    }

  companion object {
    fun decode(value: String?): RemoteWeeklySchedule {
      if (value.isNullOrBlank()) return RemoteWeeklySchedule()

      val parsed =
        value.split(";").mapNotNull { item ->
          val parts = item.split(",")
          if (parts.size != 4) return@mapNotNull null

          val day = runCatching { ScheduleDay.valueOf(parts[0]) }.getOrNull()
            ?: return@mapNotNull null
          val enabled =
            when (parts[1]) {
              "1" -> true
              "0" -> false
              else -> return@mapNotNull null
            }
          val start = parts[2].toIntOrNull()?.takeIf { it in 0..1439 }
            ?: return@mapNotNull null
          val end = parts[3].toIntOrNull()?.takeIf { it in 0..1439 }
            ?: return@mapNotNull null

          day to RemoteDaySchedule(
            enabled = enabled,
            startMinutes = start,
            endMinutes = end,
          )
        }.toMap()

      return RemoteWeeklySchedule(
        ScheduleDay.entries.associateWith { parsed[it] ?: RemoteDaySchedule() },
      )
    }
  }
}

fun parseScheduleTimeToMinutes(value: String): Int? {
  val match = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").matchEntire(value.trim())
    ?: return null
  val (hours, minutes) = match.destructured
  return hours.toInt() * 60 + minutes.toInt()
}

private fun Int.toTimeLabel(): String =
  "%02d:%02d".format(this / 60, this % 60)
