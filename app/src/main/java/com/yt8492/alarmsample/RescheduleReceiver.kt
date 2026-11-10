package com.yt8492.alarmsample

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class RescheduleReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action !in ACTIONS) return
    // 予約だけを復元する。BOOT_COMPLETEDから再生サービスは起動しない。
    runCatching { AlarmCoordinator(context).restore() }
      .onFailure { Log.e("AlarmSample", "予約を復元できませんでした", it) }
  }

  private companion object {
    val ACTIONS = buildSet {
      add(Intent.ACTION_BOOT_COMPLETED)
      add(Intent.ACTION_TIME_CHANGED)
      add(Intent.ACTION_TIMEZONE_CHANGED)
      add(Intent.ACTION_MY_PACKAGE_REPLACED)
      if (Build.VERSION.SDK_INT >= 31) {
        add(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
      }
    }
  }
}
