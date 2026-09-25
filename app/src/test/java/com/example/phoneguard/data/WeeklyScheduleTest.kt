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
  fun nextTransitionForOvernightScheduleIsStartThenEnd() {
    val schedule =
      scheduleWith(
        GuardDay.MONDAY,
        DaySchedule(enabled = true, startMinutes = 22 * 60, endMinutes = 7 * 60),
      )

    val beforeStart = time(Calendar.MONDAY, 21, 0)
    val startTransition = schedule.nextTransitionAfter(beforeStart)!!

    assertEquals(time(Calendar.MONDAY, 22, 0).timeInMillis, startTransition.triggerAtMillis)
    assertTrue(startTransition.restrictedAfter)

    val duringRestriction = time(Calendar.MONDAY, 23, 0)
    val endTransition = schedule.nextTransitionAfter(duringRestriction)!!

    assertEquals(time(Calendar.TUESDAY, 7, 0).timeInMillis, endTransition.triggerAtMillis)
    assertFalse(endTransition.restrictedAfter)
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
      // 20 Sep 2026 is Sunday, so Calendar's Sunday=1 ... Saturday=7
      // maps directly to dates 20 ... 26 without relying on DAY_OF_WEEK mutation.
      set(
        2026,
        Calendar.SEPTEMBER,
        20 + (dayOfWeek - Calendar.SUNDAY),
        hour,
        minute,
        0,
      )
    }
}
