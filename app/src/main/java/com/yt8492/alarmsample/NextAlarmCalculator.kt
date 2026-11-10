package com.yt8492.alarmsample

import java.time.ZonedDateTime

object NextAlarmCalculator {
  fun next(
    alarm: Alarm,
    now: ZonedDateTime = ZonedDateTime.now(),
  ): ZonedDateTime {
    for (offset in 0L..7L) {
      val date = now.toLocalDate().plusDays(offset)
      if (
        alarm.weekdays.isNotEmpty() &&
        date.dayOfWeek !in alarm.weekdays
      ) {
        continue
      }
      val candidate = date.atTime(alarm.time).atZone(now.zone)
      if (candidate.isAfter(now)) return candidate
    }
    error("Next alarm was not found")
  }
}
