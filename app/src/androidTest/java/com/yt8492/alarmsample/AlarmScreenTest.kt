package com.yt8492.alarmsample

import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmScreenTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()
  private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val slowSound = Uri.parse("content://com.yt8492.alarmsample.test.slowaudio/sample")
  private var importedFile: File? = null
  private var previousAlarm: Alarm? = null
  private var importStarted = false

  private fun viewModel(): AlarmViewModel {
    lateinit var value: AlarmViewModel
    compose.activityRule.scenario.onActivity { value = ViewModelProvider(it)[AlarmViewModel::class.java] }
    return value
  }

  private fun waitForDevice(timeoutMillis: Long, condition: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMillis
    var complete = condition()
    while (!complete && SystemClock.elapsedRealtime() < deadline) {
      SystemClock.sleep(50)
      complete = condition()
    }
    assertTrue("端末の処理が時間内に完了しませんでした", complete)
  }

  private fun shell(command: String) {
    android.os.ParcelFileDescriptor.AutoCloseInputStream(
      InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).use { it.readBytes() }
  }

  @Before fun wakeDevice() {
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    assumeFalse("実機をロック解除してから実行してください",
      context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
  }

  private fun capture(name: String) {
    shell("input keyevent KEYCODE_WAKEUP")
    compose.waitForIdle()
    SystemClock.sleep(200)
    val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    File(context.cacheDir, name).outputStream().use {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }
    bitmap.recycle()
  }

  private fun ringingScreenResumed(): Boolean {
    var resumed = false
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        .any { it is RingingActivity }
    }
    return resumed
  }

  @After fun cleanUp() {
    if (importStarted) runCatching { context.contentResolver.call(slowSound, "release", null, null) }
    importedFile?.delete()
    previousAlarm?.let { alarm ->
      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        context.stopService(Intent(context, AlarmRingingService::class.java))
        AlarmCoordinator(context).disable()
        AlarmStore(context).write(alarm)
      }
    }
  }

  @Test fun timeAndWeekdaySurviveRecreation() {
    val model = viewModel()
    compose.runOnIdle {
      if (DayOfWeek.WEDNESDAY in model.uiState.value.draft.weekdays) model.toggleDay(DayOfWeek.WEDNESDAY)
    }
    compose.onNodeWithTag("alarmTime").performClick()
    compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("05")
    compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("31")
    capture("compose-time-input.png")
    compose.onNodeWithTag("confirmTime").performClick()
    compose.onNodeWithTag("weekday-3").performClick()
    compose.activityRule.scenario.recreate()
    compose.onNodeWithTag("alarmTime").assertTextContains("05:31")
    compose.onNodeWithTag("weekday-3").assertIsSelected()
    capture("compose-settings.png")
  }

  @Test fun soundImportCompletesAfterRecreation() {
    importStarted = true
    val model = viewModel()
    compose.runOnIdle { model.importSound(slowSound) }
    compose.waitUntil(3000) { model.uiState.value.importing }
    compose.waitUntil(3000) { context.contentResolver.call(slowSound, "state", null, null)?.getBoolean("opened") == true }
    compose.onNodeWithTag("chooseSound").performScrollTo().assertIsNotEnabled()
    compose.activityRule.scenario.recreate()
    compose.onNodeWithTag("chooseSound").performScrollTo().assertIsNotEnabled()
    context.contentResolver.call(slowSound, "release", null, null)
    compose.waitUntil(5000) { !model.uiState.value.importing }
    compose.onNodeWithTag("soundName").performScrollTo().assertTextEquals("rotation-test.wav")
    compose.onNodeWithTag("chooseSound").assertIsEnabled()
    importedFile = SoundStore(context).resolve(model.uiState.value.draft.soundFileName)
    val expected = InstrumentationRegistry.getInstrumentation().context.assets.open("rotation-test.wav").use { it.readBytes() }
    assertArrayEquals(expected, checkNotNull(importedFile).readBytes())
  }

  @Test fun backgroundAlarmCanBeStoppedFromComposeScreen() {
    val saved = AlarmStore(context).read()
    assumeFalse("既存の予約を変更しないため、有効な予約があれば実行しない", saved.enabled)
    previousAlarm = saved
    val model = viewModel()
    compose.runOnIdle {
      // 保存操作中に分境界を越えて翌日扱いにならないよう、5秒の余裕を取る。
      model.setTime(LocalTime.now().plusSeconds(5).plusMinutes(1).withSecond(0).withNano(0))
      model.uiState.value.draft.weekdays.forEach(model::toggleDay)
    }
    compose.onNodeWithTag("saveAlarm").performScrollTo().performClick()
    compose.waitUntil(3000) { AlarmStore(context).read().enabled }
    android.util.Log.i("AlarmScreenTest", "予約を保存しました")
    shell("input keyevent KEYCODE_HOME")
    shell("input keyevent KEYCODE_SLEEP")
    // UIが停止している間はComposeの仮想時計を使わず、OSの実時間で待つ。
    waitForDevice(70_000) { AlarmRingingService.isRinging }
    android.util.Log.i("AlarmScreenTest", "鳴動サービスが起動しました")
    val audio = context.getSystemService(AudioManager::class.java)
    waitForDevice(3000) {
      audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }
    }
    android.util.Log.i("AlarmScreenTest", "USAGE_ALARMの再生を確認しました")
    // ロック中は全画面表示を待ち、自動表示されなければ通知本文を押す。
    val screenDeadline = SystemClock.elapsedRealtime() + 3000
    while (!ringingScreenResumed() && SystemClock.elapsedRealtime() < screenDeadline) SystemClock.sleep(50)
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    if (!ringingScreenResumed()) {
      shell("input keyevent KEYCODE_WAKEUP")
      shell("wm dismiss-keyguard")
      val info = automation.serviceInfo
      info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
      automation.serviceInfo = info
      shell("cmd statusbar expand-notifications")
      try {
        waitForDevice(5000) {
          val roots = automation.windows.mapNotNull { it.root } + listOfNotNull(automation.rootInActiveWindow)
          var node = roots.asSequence().flatMap {
            it.findAccessibilityNodeInfosByText("タップして画面を開くか、停止を押してください").asSequence()
          }.firstOrNull()
          while (node != null && !node.isClickable) node = node.parent
          node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
        }
      } catch (failure: AssertionError) {
        capture("compose-notification-failed.png")
        throw failure
      }
    }
    waitForDevice(3000, ::ringingScreenResumed)
    compose.waitForIdle()
    capture("compose-ringing.png")
    compose.onNodeWithTag("stopAlarm").performClick()
    waitForDevice(3000) { !AlarmRingingService.isRinging }
    assertFalse(AlarmStore(context).read().enabled)
  }
}
