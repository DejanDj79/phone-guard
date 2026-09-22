package com.example.phoneguard.data

import java.util.Calendar

enum class GuardDay(
  val calendarDay: Int,
  val displayName: String,
) {
  MONDAY(Calendar.MONDAY, "Ponedeljak"),
  TUESDAY(Calendar.TUESDAY, "Utorak"),
  WEDNESDAY(Calendar.WEDNESDAY, "Sreda"),
  THURSDAY(Calendar.THURSDAY, "Četvrtak"),
  FRIDAY(Calendar.FRIDAY, "Petak"),
  SATURDAY(Calendar.SATURDAY, "Subota"),
  SUNDAY(Calendar.SUNDAY, "Nedelja");

  companion object {
    fun fromCalendarDay(calendarDay: Int): GuardDay =
      entries.first { it.calendarDay == calendarDay }
  }
}

data class DaySchedule(
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

data class ScheduleTransition(
  val triggerAtMillis: Long,
  val restrictedAfter: Boolean,
)

data class WeeklySchedule(
  val days: Map<GuardDay, DaySchedule> =
    GuardDay.entries.associateWith { DaySchedule() },
) {
  fun scheduleFor(day: GuardDay): DaySchedule =
    days[day] ?: DaySchedule()

  fun enabledDaysCount(): Int =
    GuardDay.entries.count { scheduleFor(it).enabled }

  fun isRestrictedAt(calendar: Calendar): Boolean {
    val day = GuardDay.fromCalendarDay(calendar.get(Calendar.DAY_OF_WEEK))
    val previousDay = GuardDay.fromCalendarDay(
      if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
        Calendar.SATURDAY
      } else {
        calendar.get(Calendar.DAY_OF_WEEK) - 1
      },
    )
    val nowMinutes =
      calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

    val today = scheduleFor(day)
    if (today.enabled && today.startMinutes != today.endMinutes) {
      if (today.startMinutes < today.endMinutes) {
        if (nowMinutes in today.startMinutes until today.endMinutes) return true
      } else if (nowMinutes >= today.startMinutes) {
        return true
      }
    }

    val previous = scheduleFor(previousDay)
    return previous.enabled &&
      previous.startMinutes > previous.endMinutes &&
      nowMinutes < previous.endMinutes
  }

  fun currentUnlockTimeLabel(calendar: Calendar): String? {
    if (!isRestrictedAt(calendar)) return null

    val day = GuardDay.fromCalendarDay(calendar.get(Calendar.DAY_OF_WEEK))
    val previousDay = GuardDay.fromCalendarDay(
      if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
        Calendar.SATURDAY
      } else {
        calendar.get(Calendar.DAY_OF_WEEK) - 1
      },
    )
    val nowMinutes =
      calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

    val today = scheduleFor(day)
    if (today.enabled && today.startMinutes != today.endMinutes) {
      if (today.startMinutes < today.endMinutes &&
        nowMinutes in today.startMinutes until today.endMinutes
      ) {
        return today.endLabel
      }
      if (today.startMinutes > today.endMinutes && nowMinutes >= today.startMinutes) {
        return today.endLabel
      }
    }

    val previous = scheduleFor(previousDay)
    return if (
      previous.enabled &&
      previous.startMinutes > previous.endMinutes &&
      nowMinutes < previous.endMinutes
    ) {
      previous.endLabel
    } else {
      null
    }
  }

  fun nextTransitionAfter(calendar: Calendar): ScheduleTransition? {
    val nowMillis = calendar.timeInMillis
    val candidates = mutableSetOf<Long>()

    for (dayOffset in -1..7) {
      val dayCalendar = (calendar.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, dayOffset)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }

      val day = GuardDay.fromCalendarDay(dayCalendar.get(Calendar.DAY_OF_WEEK))
      val schedule = scheduleFor(day)
      if (!schedule.enabled || schedule.startMinutes == schedule.endMinutes) continue

      val start = (dayCalendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, schedule.startMinutes / 60)
        set(Calendar.MINUTE, schedule.startMinutes % 60)
      }

      val end = (dayCalendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, schedule.endMinutes / 60)
        set(Calendar.MINUTE, schedule.endMinutes % 60)
        if (schedule.startMinutes > schedule.endMinutes) {
          add(Calendar.DAY_OF_MONTH, 1)
        }
      }

      if (start.timeInMillis > nowMillis) candidates += start.timeInMillis
      if (end.timeInMillis > nowMillis) candidates += end.timeInMillis
    }

    val triggerAtMillis = candidates.minOrNull() ?: return null
    val after = (calendar.clone() as Calendar).apply {
      timeInMillis = triggerAtMillis
    }

    return ScheduleTransition(
      triggerAtMillis = triggerAtMillis,
      restrictedAfter = isRestrictedAt(after),
    )
  }

  fun encode(): String =
    GuardDay.entries.joinToString(";") { day ->
      val schedule = scheduleFor(day)
      listOf(
        day.name,
        if (schedule.enabled) "1" else "0",
        schedule.startMinutes.toString(),
        schedule.endMinutes.toString(),
      ).joinToString(",")
    }

  companion object {
    fun decode(value: String?): WeeklySchedule {
      if (value.isNullOrBlank()) return WeeklySchedule()

      val parsed =
        value.split(";").mapNotNull { item ->
          val parts = item.split(",")
          if (parts.size != 4) return@mapNotNull null

          val day = runCatching { GuardDay.valueOf(parts[0]) }.getOrNull()
            ?: return@mapNotNull null
          val start = parts[2].toIntOrNull()?.takeIf { it in 0..1439 }
            ?: return@mapNotNull null
          val end = parts[3].toIntOrNull()?.takeIf { it in 0..1439 }
            ?: return@mapNotNull null

          day to DaySchedule(
            enabled = parts[1] == "1",
            startMinutes = start,
            endMinutes = end,
          )
        }.toMap()

      return WeeklySchedule(
        GuardDay.entries.associateWith { parsed[it] ?: DaySchedule() },
      )
    }
  }
}

fun parseTimeToMinutes(value: String): Int? {
  val match = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").matchEntire(value.trim())
    ?: return null
  val (hours, minutes) = match.destructured
  return hours.toInt() * 60 + minutes.toInt()
}

private fun Int.toTimeLabel(): String =
  "%02d:%02d".format(this / 60, this % 60)
