package com.yt8492.alarmsample

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

class AlarmScheduler(context: Context) {
  private val context = context.applicationContext
  private val manager = context.getSystemService(AlarmManager::class.java)

  fun canSchedule(): Boolean =
    Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

  fun schedule(alarm: Alarm) {
    if (!canSchedule()) {
      throw SecurityException("アラームとリマインダーの許可が必要です")
    }
    val trigger = requireNotNull(alarm.triggerAtMillis)
    val showIntent = PendingIntent.getActivity(
      context, 0, Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val operation = PendingIntent.getBroadcast(
      context, 1, deliveryIntent(trigger),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    manager.setAlarmClock(
      AlarmManager.AlarmClockInfo(trigger, showIntent), operation,
    )
  }

  fun cancel() {
    val operation = PendingIntent.getBroadcast(
      context, 1, deliveryIntent(0L),
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) ?: return
    manager.cancel(operation)
    operation.cancel()
  }

  private fun deliveryIntent(trigger: Long): Intent =
    Intent(context, AlarmReceiver::class.java).apply {
      action = ACTION_FIRE
      data = Uri.parse("alarmsample://alarm/primary")
      putExtra(EXTRA_TRIGGER, trigger)
    }

  companion object {
    const val ACTION_FIRE = "com.yt8492.alarmsample.FIRE"
    const val EXTRA_TRIGGER = "trigger_at_millis"
  }
}
