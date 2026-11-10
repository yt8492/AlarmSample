package com.yt8492.alarmsample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlarmScreen(
  state: AlarmUiState,
  onTimeChange: (LocalTime) -> Unit,
  onToggleDay: (DayOfWeek) -> Unit,
  onChooseSound: () -> Unit,
  onDefaultSound: () -> Unit,
  onSave: () -> Unit,
  onDisable: () -> Unit,
  onStopRinging: () -> Unit,
  onNotificationSettings: () -> Unit,
  onExactAlarmSettings: () -> Unit,
  onFullScreenSettings: () -> Unit,
  onMessageShown: () -> Unit,
) {
  val snackbar = remember { SnackbarHostState() }
  var showTimeDialog by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(state.message) {
    state.message?.let {
      snackbar.showSnackbar(it)
      onMessageShown()
    }
  }
  Scaffold(
    modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.padding(padding).consumeWindowInsets(padding).testTag("alarmSettings"),
      contentPadding = PaddingValues(AlarmSpacing.screen),
      verticalArrangement = Arrangement.spacedBy(AlarmSpacing.item),
    ) {
      item {
        Text("AlarmSample", style = MaterialTheme.typography.headlineMedium)
        Text("時刻・曜日・音源を設定する、1件だけの目覚ましです。",
          modifier = Modifier.padding(top = AlarmSpacing.item))
      }
      item {
        SectionTitle("時刻")
        Button(
          onClick = { showTimeDialog = true },
          modifier = Modifier.fillMaxWidth().testTag("alarmTime"),
        ) { Text(state.draft.time.toString()) }
      }
      item {
        SectionTitle("繰り返す曜日")
        Column {
          DayOfWeek.entries.chunked(4).forEach { days ->
            Row(horizontalArrangement = Arrangement.spacedBy(AlarmSpacing.item)) {
              days.forEach { day ->
                val label = listOf("月", "火", "水", "木", "金", "土", "日")[day.ordinal]
                FilterChip(
                  selected = day in state.draft.weekdays,
                  onClick = { onToggleDay(day) },
                  label = { Text(label) },
                  modifier = Modifier.weight(1f).testTag("weekday-${day.value}")
                    .semantics { contentDescription = "${label}曜日に繰り返す" },
                )
              }
            }
          }
        }
        Text("曜日を選ばない場合は、次に来る指定時刻に1回だけ鳴ります。")
      }
      item {
        SectionTitle("音源")
        Text(state.draft.soundName ?: "システムの標準アラーム音",
          modifier = Modifier.testTag("soundName"))
      }
      item {
        OutlinedButton(onClick = onChooseSound, enabled = !state.importing,
          modifier = Modifier.fillMaxWidth().testTag("chooseSound")) {
          Text(if (state.importing) "音源を取り込み中…" else "音楽ファイルを選ぶ")
        }
      }
      item {
        OutlinedButton(onClick = onDefaultSound, enabled = !state.importing,
          modifier = Modifier.fillMaxWidth()) { Text("標準のアラーム音に戻す") }
        Text("音楽はアプリ内に取り込みます。上限20 MiB。音量は端末のアラーム音量に従います。",
          modifier = Modifier.padding(top = AlarmSpacing.item))
      }
      item {
        Button(onClick = onSave, enabled = !state.importing,
          modifier = Modifier.fillMaxWidth().testTag("saveAlarm")) { Text("保存して予約") }
      }
      item {
        OutlinedButton(onClick = onDisable, modifier = Modifier.fillMaxWidth().testTag("disableAlarm")) {
          Text("予約を解除")
        }
      }
      item {
        OutlinedButton(onClick = onStopRinging, modifier = Modifier.fillMaxWidth().testTag("stopRinging")) {
          Text("鳴っているアラームを停止")
        }
        Text(reservationStatus(state), modifier = Modifier.padding(top = AlarmSpacing.item).testTag("reservationStatus"))
      }
      item {
        SectionTitle("許可の確認")
        Text("通知：${if (state.notificationsAllowed) "許可済み" else "未許可"}\n" +
          "正確な予約：${if (state.canSchedule) "許可済み" else "未許可"}\n" +
          "全画面表示：${if (state.fullScreenAvailable) "利用可能" else "未許可（通知から操作できます）"}")
      }
      item {
        OutlinedButton(onClick = onNotificationSettings, modifier = Modifier.fillMaxWidth()) {
          Text("通知の許可・設定")
        }
      }
      item {
        OutlinedButton(onClick = onExactAlarmSettings, modifier = Modifier.fillMaxWidth()) {
          Text("アラームとリマインダーの許可")
        }
      }
      item {
        OutlinedButton(onClick = onFullScreenSettings, modifier = Modifier.fillMaxWidth()) {
          Text("ロック画面での全画面表示の許可")
        }
        Text("鳴動は最大10分です。全画面表示が許可されていない場合は、通知から停止できます。",
          modifier = Modifier.padding(top = AlarmSpacing.item))
      }
    }
  }
  if (showTimeDialog) {
    AlarmTimeDialog(
      time = state.draft.time,
      onDismiss = { showTimeDialog = false },
      onConfirm = { onTimeChange(it); showTimeDialog = false },
    )
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(top = AlarmSpacing.section, bottom = AlarmSpacing.item))
}

private fun reservationStatus(state: AlarmUiState): String = when {
  !state.reservedAlarm.enabled -> "予約はありません"
  !state.canSchedule -> "予約の許可がありません。許可後にアプリを開いてください"
  state.reservedAlarm.triggerAtMillis != null -> "次回：" +
    Instant.ofEpochMilli(state.reservedAlarm.triggerAtMillis).atZone(ZoneId.systemDefault())
      .format(DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.JAPAN))
  else -> "次回の予約を確認してください"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimeDialog(time: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
  val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("アラームの時刻") },
    text = { TimeInput(state = picker, modifier = Modifier.testTag("timeInput")) },
    confirmButton = {
      TextButton(onClick = { onConfirm(LocalTime.of(picker.hour, picker.minute)) },
        modifier = Modifier.testTag("confirmTime")) { Text("決定") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

@Composable
fun RingingScreen(onStop: () -> Unit) {
  Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(AlarmSpacing.screen),
      verticalArrangement = Arrangement.spacedBy(AlarmSpacing.section),
    ) {
      Text("アラーム", style = MaterialTheme.typography.headlineMedium)
      Text("停止ボタンで音を止めて、この画面を閉じます。\n繰り返しの次回予約はそのまま残ります。")
      Button(onClick = onStop,
        modifier = Modifier.fillMaxWidth().heightIn(min = AlarmSpacing.stopButton).testTag("stopAlarm")) {
        Text("停止")
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 850)
@Composable
private fun AlarmScreenPreview() {
  AlarmSampleTheme {
    AlarmScreen(
      state = AlarmUiState(), onTimeChange = {}, onToggleDay = {},
      onChooseSound = {}, onDefaultSound = {}, onSave = {}, onDisable = {}, onStopRinging = {},
      onNotificationSettings = {}, onExactAlarmSettings = {}, onFullScreenSettings = {}, onMessageShown = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun RingingScreenPreview() {
  AlarmSampleTheme { RingingScreen(onStop = {}) }
}
