package com.yt8492.alarmsample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
  private val viewModel: AlarmViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    AlarmNotifications(this)
    setContent {
      AlarmSampleTheme {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val chooseSound = rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocument(),
        ) { uri ->
          if (uri != null) viewModel.importSound(uri)
        }
        val requestNotifications = rememberLauncherForActivityResult(
          ActivityResultContracts.RequestPermission(),
        ) { granted ->
          viewModel.showMessage(
            if (granted) "通知が許可されました。予約ボタンを押してください"
            else "通知の許可が必要です",
          )
          viewModel.refreshStatus()
        }
        val openNotifications = {
          if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
              this, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
              .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
          }
        }
        AlarmScreen(
          state = state,
          onTimeChange = viewModel::setTime,
          onToggleDay = viewModel::toggleDay,
          onChooseSound = { chooseSound.launch(arrayOf("audio/*")) },
          onDefaultSound = viewModel::useDefaultSound,
          onSave = {
            when {
              !state.notificationsAllowed -> {
                viewModel.showMessage("停止操作のため、先に通知を許可してください")
                openNotifications()
              }
              !state.canSchedule -> openExactAlarmSettings()
              else -> viewModel.saveAlarm()
            }
          },
          onDisable = viewModel::disableAlarm,
          onStopRinging = { stopService(Intent(this, AlarmRingingService::class.java)) },
          onNotificationSettings = openNotifications,
          onExactAlarmSettings = ::openExactAlarmSettings,
          onFullScreenSettings = {
            if (Build.VERSION.SDK_INT >= 34) {
              openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:$packageName")))
            } else viewModel.showMessage("このAndroidバージョンでは専用の許可画面はありません")
          },
          onMessageShown = viewModel::clearMessage,
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.restoreAndRefresh()
  }

  private fun openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT >= 31) {
      openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:$packageName")))
    } else viewModel.showMessage("このAndroidバージョンでは追加の許可は不要です")
  }

  private fun openSettings(intent: Intent) {
    runCatching { startActivity(intent) }
      .onFailure { viewModel.showMessage("この端末では設定画面を開けませんでした") }
  }
}
