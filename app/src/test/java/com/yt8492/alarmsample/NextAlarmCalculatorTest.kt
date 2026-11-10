package com.yt8492.alarmsample

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextAlarmCalculatorTest {
  private val weekdays = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
  )

  @Test fun beforeTimeUsesToday() = checkNext("2026-11-09T06:59:00+09:00[Asia/Tokyo]", "2026-11-09T07:00:00+09:00[Asia/Tokyo]")
  @Test fun pastTimeUsesTomorrow() = checkNext("2026-11-09T07:01:00+09:00[Asia/Tokyo]", "2026-11-10T07:00:00+09:00[Asia/Tokyo]")
  @Test fun equalTimeUsesTomorrow() = checkNext("2026-11-09T07:00:00+09:00[Asia/Tokyo]", "2026-11-10T07:00:00+09:00[Asia/Tokyo]")
  @Test fun secondsPastTimeUseTomorrow() = checkNext("2026-11-09T07:00:01+09:00[Asia/Tokyo]", "2026-11-10T07:00:00+09:00[Asia/Tokyo]")
  @Test fun fridayAfterTimeUsesMonday() {
    val alarm = Alarm(time = LocalTime.of(7, 0), weekdays = weekdays)
    val now = ZonedDateTime.parse(
      "2026-11-13T07:01:00+09:00[Asia/Tokyo]",
    )
    val expected = ZonedDateTime.parse(
      "2026-11-16T07:00:00+09:00[Asia/Tokyo]",
    )
    assertEquals(expected, NextAlarmCalculator.next(alarm, now))
  }
  @Test fun sundayUsesMonday() = checkNext("2026-11-15T06:00:00+09:00[Asia/Tokyo]", "2026-11-16T07:00:00+09:00[Asia/Tokyo]", weekdays)
  @Test fun selectedTodayBeforeTimeUsesToday() = checkNext("2026-11-09T06:59:00+09:00[Asia/Tokyo]", "2026-11-09T07:00:00+09:00[Asia/Tokyo]", setOf(DayOfWeek.MONDAY))
  @Test fun selectedTodayAtTimeUsesNextWeek() = checkNext("2026-11-09T07:00:00+09:00[Asia/Tokyo]", "2026-11-16T07:00:00+09:00[Asia/Tokyo]", setOf(DayOfWeek.MONDAY))
  @Test fun crossesYearBoundary() = checkNext("2026-12-31T08:00:00+09:00[Asia/Tokyo]", "2027-01-01T07:00:00+09:00[Asia/Tokyo]")

  @Test fun springGapMovesToFirstValidOffset() {
    val alarm = Alarm(time = LocalTime.of(2, 30))
    val now = ZonedDateTime.parse("2026-03-08T00:00:00-05:00[America/New_York]")
    assertEquals(ZonedDateTime.parse("2026-03-08T03:30:00-04:00[America/New_York]"), NextAlarmCalculator.next(alarm, now))
  }

  @Test fun autumnOverlapUsesEarlierOffsetOnce() {
    val alarm = Alarm(time = LocalTime.of(1, 30))
    val now = ZonedDateTime.parse("2026-11-01T01:45:00-04:00[America/New_York]")
    assertEquals(ZonedDateTime.parse("2026-11-02T01:30:00-05:00[America/New_York]"), NextAlarmCalculator.next(alarm, now))
  }

  @Test fun enabledMatchingOccurrenceIsAccepted() {
    assertTrue(Alarm(enabled = true, triggerAtMillis = 100L).isCurrentOccurrence(100L))
  }

  @Test fun oldOccurrenceAfterEditIsIgnored() {
    assertFalse(Alarm(enabled = true, triggerAtMillis = 200L).isCurrentOccurrence(100L))
  }

  @Test fun disabledOrConsumedOccurrenceIsIgnored() {
    assertFalse(Alarm(enabled = false, triggerAtMillis = 100L).isCurrentOccurrence(100L))
    assertFalse(Alarm(enabled = true, triggerAtMillis = null).isCurrentOccurrence(100L))
  }

  private fun checkNext(now: String, expected: String, days: Set<DayOfWeek> = emptySet()) {
    val alarm = Alarm(time = LocalTime.of(7, 0), weekdays = days)
    assertEquals(ZonedDateTime.parse(expected), NextAlarmCalculator.next(alarm, ZonedDateTime.parse(now)))
  }
}
