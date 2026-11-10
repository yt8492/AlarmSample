package com.yt8492.alarmsample

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AlarmUiState(
  val draft: Alarm = Alarm(),
  val reservedAlarm: Alarm = Alarm(),
  val importing: Boolean = false,
  val notificationsAllowed: Boolean = false,
  val canSchedule: Boolean = false,
  val fullScreenAvailable: Boolean = false,
  val message: String? = null,
)

class AlarmViewModel(
  application: Application,
  private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
  private val store = AlarmStore(application)
  private val coordinator = AlarmCoordinator(application)
  private val soundStore = SoundStore(application)
  private val initial = store.read()
  private val restoredDraft = savedState.contains("time")
  private val state = MutableStateFlow(AlarmUiState(
    draft = initial.copy(
      time = savedState.get<String>("time")?.let(LocalTime::parse) ?: initial.time,
      weekdays = savedState.get<IntArray>("days")?.map(DayOfWeek::of)?.toSet()
        ?: initial.weekdays,
      soundFileName = if (restoredDraft) savedState["sound_file"] else initial.soundFileName,
      soundName = if (restoredDraft) savedState["sound_name"] else initial.soundName,
    ),
    reservedAlarm = initial,
  ))
  val uiState = state.asStateFlow()

  init {
    rememberDraft(state.value.draft)
    refreshStatus()
  }

  fun setTime(time: LocalTime) = updateDraft { it.copy(time = time) }

  fun toggleDay(day: DayOfWeek) = updateDraft {
    it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day)
  }

  fun useDefaultSound() = updateDraft { it.copy(soundFileName = null, soundName = null) }

  private fun updateDraft(change: (Alarm) -> Alarm) {
    val draft = change(state.value.draft)
    rememberDraft(draft)
    state.update { it.copy(draft = draft) }
  }

  private fun rememberDraft(draft: Alarm) {
    savedState["time"] = draft.time.toString()
    savedState["days"] = draft.weekdays.map { it.value }.toIntArray()
    savedState["sound_file"] = draft.soundFileName
    savedState["sound_name"] = draft.soundName
  }

  fun restoreAndRefresh() {
    runCatching { coordinator.restoreOnAppOpen() }
      .onFailure { showMessage(it.message ?: "予約を復元できませんでした") }
    refreshStatus()
  }

  fun refreshStatus() {
    val context = getApplication<Application>()
    val manager = context.getSystemService(NotificationManager::class.java)
    val runtimeGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
      context, Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    state.update { it.copy(
      reservedAlarm = store.read(),
      notificationsAllowed = runtimeGranted && manager.areNotificationsEnabled() &&
        manager.getNotificationChannel("alarm_ringing")?.importance != NotificationManager.IMPORTANCE_NONE,
      canSchedule = AlarmScheduler(context).canSchedule(),
      fullScreenAvailable = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent(),
    ) }
  }

  fun saveAlarm() {
    if (state.value.importing) return
    runCatching { coordinator.enable(state.value.draft) }
      .onSuccess { alarm ->
        if (!AlarmRingingService.isRinging) soundStore.cleanUp(setOfNotNull(alarm.soundFileName))
        showMessage("予約しました")
      }
      .onFailure { showMessage(it.message ?: "予約できませんでした") }
    refreshStatus()
  }

  fun disableAlarm() {
    runCatching { coordinator.disable() }
      .onSuccess { showMessage("予約を解除しました") }
      .onFailure { showMessage(it.message ?: "予約を解除できませんでした") }
    refreshStatus()
  }

  fun importSound(uri: Uri) {
    if (state.value.importing) return
    state.update { it.copy(importing = true) }
    viewModelScope.launch {
      var imported: ImportedSound? = null
      var applied = false
      try {
        // ViewModelのスコープで動かし、画面再生成では取り込みを中断しない。
        withContext(Dispatchers.IO) { imported = soundStore.import(uri) }
        val sound = checkNotNull(imported)
        updateDraft { it.copy(soundFileName = sound.fileName, soundName = sound.displayName) }
        applied = true
        showMessage("音源を取り込みました。予約ボタンで保存してください")
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        showMessage(error.message ?: "音源を取り込めませんでした")
      } finally {
        // 画面を終了して結果を採用できなかったときは、作成済みの音源を残さない。
        if (!applied) withContext(NonCancellable + Dispatchers.IO) {
          imported?.let { soundStore.resolve(it.fileName)?.delete() }
        }
        state.update { it.copy(importing = false) }
      }
    }
  }

  fun showMessage(message: String) = state.update { it.copy(message = message) }
  fun clearMessage() = state.update { it.copy(message = null) }
}
