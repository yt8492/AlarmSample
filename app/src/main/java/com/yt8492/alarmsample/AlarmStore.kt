package com.yt8492.alarmsample

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalTime

class AlarmStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("alarm", Context.MODE_PRIVATE)

  fun read(): Alarm {
    val mask = prefs.getInt("weekdays", 0)
    return Alarm(
      time = LocalTime.of(prefs.getInt("hour", 7), prefs.getInt("minute", 0)),
      weekdays = DayOfWeek.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet(),
      enabled = prefs.getBoolean("enabled", false),
      soundFileName = prefs.getString("sound_file", null),
      soundName = prefs.getString("sound_name", null),
      triggerAtMillis = if (prefs.contains("trigger")) prefs.getLong("trigger", 0L) else null,
    )
  }

  fun write(alarm: Alarm) {
    val editor = prefs.edit()
      .putInt("hour", alarm.time.hour)
      .putInt("minute", alarm.time.minute)
      .putInt("weekdays", alarm.weekdays.fold(0) { mask, day -> mask or (1 shl day.ordinal) })
      .putBoolean("enabled", alarm.enabled)
      .putString("sound_file", alarm.soundFileName)
      .putString("sound_name", alarm.soundName)
    alarm.triggerAtMillis?.let { editor.putLong("trigger", it) } ?: editor.remove("trigger")
    // Receiver終了前にも保存を完了させる。扱うのは1件の小さな設定だけ。
    check(editor.commit()) { "アラーム設定を保存できませんでした" }
  }
}
