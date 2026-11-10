package com.yt8492.alarmsample

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmBehaviorTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context: Context get() = instrumentation.targetContext
  private val store get() = AlarmStore(context)
  private var original: Alarm? = null
  private var previousNotifications = emptyList<android.service.notification.StatusBarNotification>()

  private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

  private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
    instrumentation.uiAutomation.executeShellCommand(command),
  ).bufferedReader().use { it.readText() }

  private fun waitFor(timeoutMillis: Long = 5000, condition: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMillis
    while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
    assertTrue("実機の状態が時間内に変化しませんでした", condition())
  }

  private fun held(tag: String) = shell("dumpsys power").lineSequence()
    .any { it.contains("PARTIAL_WAKE_LOCK") && it.contains("'$tag'") }

  private fun overdue(repeating: Boolean): Alarm {
    val now = ZonedDateTime.now()
    return checkNotNull(original).copy(
      time = now.toLocalTime().minusMinutes(1).withSecond(0).withNano(0),
      weekdays = if (repeating) setOf(now.dayOfWeek) else emptySet(),
      enabled = true,
      triggerAtMillis = now.toInstant().toEpochMilli() - 1000,
    )
  }

  private fun fire(alarm: Alarm, receiverContext: Context = context) = onMain {
    AlarmReceiver().onReceive(receiverContext, Intent(AlarmScheduler.ACTION_FIRE)
      .putExtra(AlarmScheduler.EXTRA_TRIGGER, checkNotNull(alarm.triggerAtMillis)))
  }

  private fun waitForPlayback() {
    val audio = context.getSystemService(AudioManager::class.java)
    waitFor { AlarmRingingService.isRinging && audio.activePlaybackConfigurations
      .any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM } }
  }

  @Before fun prepare() {
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    assumeFalse("実機のロックを解除してください",
      context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    val saved = store.read()
    assumeFalse("既存の予約と鳴動は変更しません", saved.enabled || AlarmRingingService.isRinging)
    assumeTrue("正確なアラームの許可が必要です", AlarmScheduler(context).canSchedule())
    previousNotifications = context.getSystemService(NotificationManager::class.java).activeNotifications.toList()
    original = saved
  }

  @After fun cleanUp() {
    original?.let { saved ->
      onMain {
        context.stopService(Intent(context, AlarmRingingService::class.java))
        AlarmCoordinator(context).disable()
        store.write(saved)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        listOf(Stage.RESUMED, Stage.STARTED, Stage.STOPPED, Stage.CREATED)
          .flatMap(monitor::getActivitiesInStage).toSet()
          .filter { it is MainActivity || it is RingingActivity }.forEach { it.finish() }
      }
      waitFor { !AlarmRingingService.isRinging && !held("AlarmSample:ringing") }
      val manager = context.getSystemService(NotificationManager::class.java)
      manager.cancelAll()
      previousNotifications.forEach { manager.notify(it.tag, it.id, it.notification) }
      assertEquals(saved, store.read())
    }
  }

  private fun verifyLateDelivery(repeating: Boolean) {
    val alarm = overdue(repeating)
    store.write(alarm)
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.recreate()
      assertEquals("画面再表示で今回分を書き換えない", alarm, store.read())
      // OSの配信が遅れ、画面再表示の後にonReceiveへ届く順序を再現する。
      fire(alarm)
      waitForPlayback()
      val consumed = store.read()
      android.util.Log.i("AlarmBehaviorTest", "late_delivery repeating=$repeating old=${alarm.triggerAtMillis} consumed=$consumed")
      assertEquals(repeating, consumed.enabled)
      if (repeating) assertTrue(checkNotNull(consumed.triggerAtMillis) > System.currentTimeMillis())
      else assertNull(consumed.triggerAtMillis)
      onMain { assertNull(AlarmCoordinator(context).consume(checkNotNull(alarm.triggerAtMillis))) }
      assertEquals("古い配信で次回分を書き換えない", consumed, store.read())
    }
  }

  @Test fun lateOneShotSurvivesActivityRecreationAndRings() = verifyLateDelivery(false)

  @Test fun lateRepeatSurvivesActivityRecreationAndSchedulesNext() = verifyLateDelivery(true)

  @Test fun futureOccurrenceIsRestoredWithoutRecalculation() {
    val trigger = System.currentTimeMillis() + 120_000
    val next = context.getSystemService(AlarmManager::class.java).nextAlarmClock
    assumeTrue("他アプリの直近の予約は変更しません", next == null || next.triggerTime > trigger)
    val alarm = overdue(true).copy(triggerAtMillis = trigger)
    store.write(alarm)
    onMain { AlarmCoordinator(context).restoreOnAppOpen() }
    assertEquals(alarm, store.read())
    assertEquals(trigger, checkNotNull(context.getSystemService(AlarmManager::class.java).nextAlarmClock).triggerTime)
    android.util.Log.i("AlarmBehaviorTest", "future_restore stored_and_os_trigger=$trigger")
  }

  @Test fun systemRestoreExpiresSingleAndRecalculatesRepeat() {
    store.write(overdue(false))
    onMain { RescheduleReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED)) }
    assertFalse(store.read().enabled)
    assertNull(store.read().triggerAtMillis)
    store.write(overdue(true))
    onMain { RescheduleReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED)) }
    assertTrue(store.read().enabled)
    assertTrue(checkNotNull(store.read().triggerAtMillis) > System.currentTimeMillis())
    android.util.Log.i("AlarmBehaviorTest", "system_restore expired_single=true future_repeat=${store.read().triggerAtMillis}")
  }

  @Test fun handoffHeldUntilServiceAcquiresRingingLock() {
    val alarm = overdue(false)
    store.write(alarm)
    var sawLaunchLock = false
    val intercept = object : ContextWrapper(context) {
      override fun startForegroundService(service: Intent): ComponentName? {
        sawLaunchLock = held("AlarmSample:launch")
        assertTrue("サービス起動前に引き継ぎ用を保持する", sawLaunchLock)
        assertFalse(held("AlarmSample:ringing"))
        return super.startForegroundService(service)
      }
    }
    fire(alarm, intercept)
    waitForPlayback()
    assertTrue(sawLaunchLock)
    assertTrue(held("AlarmSample:ringing"))
    waitFor { !held("AlarmSample:launch") }
    android.util.Log.i("AlarmBehaviorTest", "handoff launch_before=true ringing_before=false ringing_after=true launch_after=false")
  }

  @Test fun handoffReleasedWhenServiceStartFails() {
    val alarm = overdue(false)
    store.write(alarm)
    val intercept = object : ContextWrapper(context) {
      override fun startForegroundService(service: Intent): ComponentName? {
        assertTrue(held("AlarmSample:launch"))
        throw IllegalStateException("実機テストでサービス起動失敗を再現")
      }
    }
    fire(alarm, intercept)
    assertFalse(held("AlarmSample:launch"))
    assertFalse(AlarmRingingService.isRinging)
    android.util.Log.i("AlarmBehaviorTest", "startup_failure launch_released=true service_started=false")
  }

  @Test fun handoffRequiresOwnerAndExpiresWithoutService() {
    lateinit var token: String
    onMain {
      token = AlarmLaunchWakeLock.acquire(context, timeoutMillis = 1500)
      val unheld = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmSample:test-owner")
      assertThrows(IllegalStateException::class.java) { AlarmLaunchWakeLock.complete(token, unheld) }
    }
    assertTrue("引き継ぎ先が未取得なら解放しない", held("AlarmSample:launch"))
    waitFor { !held("AlarmSample:launch") }
    onMain { AlarmLaunchWakeLock.release(token) }
    android.util.Log.i("AlarmBehaviorTest", "handoff rejected_unheld_owner=true expired_without_service=true")
  }

  @Test fun duckingFromAnotherUidPausesAndResumesPlayback() {
    val testPackage = instrumentation.context.packageName
    val result = AtomicInteger(-1)
    val helperUid = AtomicInteger(-1)
    val released = AtomicBoolean(false)
    val failure = AtomicReference<String?>(null)
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra("state") == "released") released.set(true)
        else {
          result.set(intent.getIntExtra("result", -1))
          helperUid.set(intent.getIntExtra("uid", -1))
        }
      }
    }
    ContextCompat.registerReceiver(context, receiver, IntentFilter(DuckingFocusActivity.STATE),
      ContextCompat.RECEIVER_EXPORTED)
    lateinit var player: AlarmPlayer
    val mediaField = AlarmPlayer::class.java.getDeclaredField("player").apply { isAccessible = true }
    fun isPlaying(): Boolean {
      var playing = false
      onMain { playing = (mediaField.get(player) as? MediaPlayer)?.isPlaying == true }
      return playing
    }
    ActivityScenario.launch(MainActivity::class.java).use {
      onMain {
        player = AlarmPlayer(context, failure::set)
        player.start(checkNotNull(original).soundFileName)
      }
      try {
        waitFor { failure.get() != null || isPlaying() }
        assertNull(failure.get())
        android.util.Log.i("AlarmBehaviorTest", "ducking playing_before=true target_uid=${context.applicationInfo.uid}")
        context.startActivity(Intent().setComponent(ComponentName(testPackage, DuckingFocusActivity::class.java.name))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("target_package", context.packageName))
        waitFor { result.get() != -1 }
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, result.get())
        assertNotEquals("別UIDから要求してOSの自動ducking経路を通す", context.applicationInfo.uid, helperUid.get())
        waitFor { !isPlaying() }
        assertNull(failure.get())
        android.util.Log.i("AlarmBehaviorTest", "ducking helper_uid=${helperUid.get()} focus_granted=${result.get()} playing_during=false")
        context.sendBroadcast(Intent(DuckingFocusActivity.RELEASE).setPackage(testPackage))
        waitFor { released.get() && isPlaying() }
        assertNull(failure.get())
        android.util.Log.i("AlarmBehaviorTest", "ducking helper_released=true playing_after=true")
      } finally {
        context.sendBroadcast(Intent(DuckingFocusActivity.RELEASE).setPackage(testPackage))
        onMain { player.stop() }
        context.unregisterReceiver(receiver)
      }
    }
  }
}
