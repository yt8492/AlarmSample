package com.yt8492.alarmsample

import java.time.DayOfWeek
import java.time.LocalTime

data class Alarm(
  val time: LocalTime = LocalTime.of(7, 0),
  val weekdays: Set<DayOfWeek> = emptySet(),
  val enabled: Boolean = false,
  val soundFileName: String? = null,
  val soundName: String? = null,
  val triggerAtMillis: Long? = null,
) {
  fun isCurrentOccurrence(trigger: Long): Boolean =
    enabled && triggerAtMillis == trigger
}
