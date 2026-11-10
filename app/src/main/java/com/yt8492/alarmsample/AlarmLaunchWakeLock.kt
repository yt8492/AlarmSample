package com.yt8492.alarmsample

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.MainThread
import java.util.UUID

// ReceiverとServiceは同じプロセスのメインスレッドで動かす。
object AlarmLaunchWakeLock {
  const val EXTRA_TOKEN = "launch_wake_lock_token"
  private val handler = Handler(Looper.getMainLooper())
  private data class Held(
    val lock: PowerManager.WakeLock, val expiry: Runnable,
  )
  private val pending = mutableMapOf<String, Held>()

  @MainThread
  fun acquire(context: Context, timeoutMillis: Long = 30_000L): String {
    require(timeoutMillis > 0)
    val token = UUID.randomUUID().toString()
    val manager = context.applicationContext
      .getSystemService(PowerManager::class.java)
    val lock = manager.newWakeLock(
      PowerManager.PARTIAL_WAKE_LOCK, "AlarmSample:launch",
    )
    lock.setReferenceCounted(false)
    lock.acquire(timeoutMillis)
    val expiry = Runnable { release(token) }
    pending[token] = Held(lock, expiry)
    handler.postDelayed(expiry, timeoutMillis)
    return token
  }

  @MainThread
  fun complete(token: String?, ringingLock: PowerManager.WakeLock) {
    check(ringingLock.isHeld) { "鳴動用WakeLockを取得してから引き継ぎます" }
    release(token)
  }

  @MainThread
  fun release(token: String?) {
    val held = pending.remove(token) ?: return
    handler.removeCallbacks(held.expiry)
    if (held.lock.isHeld) held.lock.release()
  }
}
