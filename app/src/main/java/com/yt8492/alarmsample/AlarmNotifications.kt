package com.yt8492.alarmsample

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmNotifications(private val context: Context) {
  private val manager = context.getSystemService(NotificationManager::class.java)

  init {
    val channel = NotificationChannel(
      CHANNEL, "アラーム", NotificationManager.IMPORTANCE_HIGH,
    ).apply {
      description = "鳴動中のアラームと停止操作"
      // 音はMediaPlayerだけで鳴らし、通知チャンネルとの二重再生を防ぐ。
      setSound(null, null)
      enableVibration(false)
    }
    manager.createNotificationChannel(channel)
  }

  fun ringing(): Notification {
    val screen = PendingIntent.getActivity(
      context, 2, Intent(context, RingingActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val stop = PendingIntent.getService(
      context, 3,
      Intent(context, AlarmRingingService::class.java)
        .setAction(AlarmRingingService.ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(R.drawable.ic_alarm)
      .setContentTitle("アラーム")
      .setContentText("タップして画面を開くか、停止を押してください")
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setForegroundServiceBehavior(
        NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE,
      )
      .setContentIntent(screen)
      .addAction(0, "停止", stop)
    if (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) {
      builder.setFullScreenIntent(screen, true)
    }
    return builder.build()
  }

  fun showError(message: String) {
    val open = PendingIntent.getActivity(
      context, 0, Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(R.drawable.ic_alarm)
      .setContentTitle("アラームを確認してください")
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setContentIntent(open)
      .setAutoCancel(true)
      .build()
    // 通知が拒否されている場合でも、エラー報告によってクラッシュさせない。
    runCatching { manager.notify(ERROR_ID, notification) }
  }

  companion object {
    const val RINGING_ID = 1
    private const val ERROR_ID = 2
    private const val CHANNEL = "alarm_ringing"
  }
}
