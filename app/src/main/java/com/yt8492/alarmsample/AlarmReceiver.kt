package com.yt8492.alarmsample

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != AlarmScheduler.ACTION_FIRE) return
    val trigger = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER, -1L)
    runCatching {
      val alarm = AlarmCoordinator(context).consume(trigger) ?: return
      val token = AlarmLaunchWakeLock.acquire(context)
      try {
        val serviceIntent = Intent(
          context, AlarmRingingService::class.java,
        ).apply {
          action = AlarmRingingService.ACTION_START
          putExtra(AlarmRingingService.EXTRA_SOUND_FILE, alarm.soundFileName)
          putExtra(AlarmLaunchWakeLock.EXTRA_TOKEN, token)
        }
        checkNotNull(
          ContextCompat.startForegroundService(context, serviceIntent),
        )
      } catch (error: Exception) {
        AlarmLaunchWakeLock.release(token)
        throw error
      }
    }.onFailure {
      Log.e("AlarmSample", "鳴動サービスを起動できませんでした", it)
      AlarmNotifications(context).showError(
        "アラームを開始できませんでした。" +
          "アプリの許可を確認してください。",
      )
    }
  }
}
