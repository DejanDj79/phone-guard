package com.example.phoneguard.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyScheduleTest {
  @Test
  fun overnightScheduleLocksBeforeMidnightAndAfterMidnight() {
    val schedule =
      scheduleWith(
        GuardDay.MONDAY,
        DaySchedule(enabled = true, startMinutes = 22 * 60, endMinutes = 7 * 60),
      )

    assertTrue(schedule.isRestrictedAt(time(Calendar.MONDAY, 22, 30)))
    assertTrue(schedule.isRestrictedAt(time(Calendar.TUESDAY, 6, 59)))
    assertFalse(schedule.isRestrictedAt(time(Calendar.TUESDAY, 7, 0)))
  }

  @Test
  fun daytimeScheduleOnlyLocksInsideSameDayWindow() {
    val schedule =
      scheduleWith(
        GuardDay.WEDNESDAY,
        DaySchedule(enabled = true, startMinutes = 14 * 60, endMinutes = 18 * 60),
      )

    assertFalse(schedule.isRestrictedAt(time(Calendar.WEDNESDAY, 13, 59)))
    assertTrue(schedule.isRestrictedAt(time(Calendar.WEDNESDAY, 14, 0)))
    assertTrue(schedule.isRestrictedAt(time(Calendar.WEDNESDAY, 17, 59)))
    assertFalse(schedule.isRestrictedAt(time(Calendar.WEDNESDAY, 18, 0)))
  }

  @Test
  fun disabledDayNeverStartsARestriction() {
    val schedule =
      scheduleWith(
        GuardDay.FRIDAY,
        DaySchedule(enabled = false, startMinutes = 22 * 60, endMinutes = 7 * 60),
      )

    assertFalse(schedule.isRestrictedAt(time(Calendar.FRIDAY, 23, 0)))
    assertFalse(schedule.isRestrictedAt(time(Calendar.SATURDAY, 6, 0)))
  }

  @Test
  fun codecPreservesWeeklySchedule() {
    val original =
      WeeklySchedule(
        GuardDay.entries.associateWith { day ->
          if (day == GuardDay.SATURDAY) {
            DaySchedule(enabled = true, startMinutes = 23 * 60, endMinutes = 9 * 60)
          } else {
            DaySchedule()
          }
        },
      )

    assertEquals(original, WeeklySchedule.decode(original.encode()))
  }

  @Test
  fun parseTimeValidatesTwentyFourHourClock() {
    assertEquals(22 * 60 + 15, parseTimeToMinutes("22:15"))
    assertEquals(0, parseTimeToMinutes("00:00"))
    assertEquals(null, parseTimeToMinutes("24:00"))
    assertEquals(null, parseTimeToMinutes("7:00"))
  }

  private fun scheduleWith(day: GuardDay, daySchedule: DaySchedule): WeeklySchedule =
    WeeklySchedule(
      GuardDay.entries.associateWith {
        if (it == day) daySchedule else DaySchedule()
      },
    )

  private fun time(dayOfWeek: Int, hour: Int, minute: Int): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      clear()
      set(Calendar.YEAR, 2026)
      set(Calendar.MONTH, Calendar.SEPTEMBER)
      set(Calendar.DAY_OF_MONTH, 21)
      set(Calendar.DAY_OF_WEEK, dayOfWeek)
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
    }
}
