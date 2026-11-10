package com.yt8492.alarmsample

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat

class AlarmRingingService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private lateinit var notifications: AlarmNotifications
  private lateinit var player: AlarmPlayer
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onCreate() {
    super.onCreate()
    notifications = AlarmNotifications(this)
    player = AlarmPlayer(this) { message ->
      notifications.showError(message)
      stopSelf()
    }
  }

  override fun onStartCommand(
    intent: Intent?, flags: Int, startId: Int,
  ): Int {
    val token = intent?.getStringExtra(AlarmLaunchWakeLock.EXTRA_TOKEN)
    try {
      when (intent?.action) {
        ACTION_START -> {
          // Android 15以降の音声フォーカス取得条件を満たしてから再生する。
          ServiceCompat.startForeground(
            this, AlarmNotifications.RINGING_ID, notifications.ringing(),
            if (Build.VERSION.SDK_INT >= 29) {
              ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else 0,
          )
          isRinging = true
          handler.removeCallbacksAndMessages(null)
          val previous = wakeLock
          wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(
              PowerManager.PARTIAL_WAKE_LOCK, "AlarmSample:ringing",
            )
            .apply { acquire(MAX_RINGING_MILLIS + 30_000L) }
          previous?.let { if (it.isHeld) it.release() }
          AlarmLaunchWakeLock.complete(token, checkNotNull(wakeLock))
          handler.postDelayed({ stopSelf() }, MAX_RINGING_MILLIS)
          player.start(intent.getStringExtra(EXTRA_SOUND_FILE))
        }
        else -> stopSelf()
      }
    } catch (error: Exception) {
      Log.e("AlarmSample", "鳴動を開始できませんでした", error)
      notifications.showError("アラームを開始できませんでした")
      stopSelf()
    } finally {
      // 起動失敗や開始以外のIntentでも、引き継ぎ用を残さない。
      AlarmLaunchWakeLock.release(token)
    }
    // プロセスが破棄された後に、古いアラームを勝手に再開しない。
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    isRinging = false
    handler.removeCallbacksAndMessages(null)
    player.stop()
    releaseWakeLock()
    stopForeground(STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  private fun releaseWakeLock() {
    wakeLock?.let { if (it.isHeld) it.release() }
    wakeLock = null
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val ACTION_START = "com.yt8492.alarmsample.START"
    const val ACTION_STOP = "com.yt8492.alarmsample.STOP"
    const val EXTRA_SOUND_FILE = "sound_file"
    private const val MAX_RINGING_MILLIS = 10 * 60 * 1000L
    var isRinging: Boolean = false
      private set
  }
}
