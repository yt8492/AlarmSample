package com.yt8492.alarmsample

import android.content.Context
import android.util.Log
import java.time.ZonedDateTime

class AlarmCoordinator(context: Context) {
  private val store = AlarmStore(context)
  private val scheduler = AlarmScheduler(context)

  fun enable(draft: Alarm): Alarm {
    val next = draft.copy(
      enabled = true,
      triggerAtMillis = NextAlarmCalculator.next(draft)
        .toInstant().toEpochMilli(),
    )
    try {
      scheduler.schedule(next)
      store.write(next)
    } catch (error: Exception) {
      scheduler.cancel()
      store.write(draft.copy(enabled = false, triggerAtMillis = null))
      throw error
    }
    return next
  }

  fun disable() {
    scheduler.cancel()
    store.write(store.read().copy(enabled = false, triggerAtMillis = null))
  }

  fun consume(trigger: Long): Alarm? {
    val alarm = store.read()
    if (!alarm.isCurrentOccurrence(trigger)) return null
    // 先に今回分を消費済みにして、同じ配信を二重に処理しない。
    store.write(alarm.copy(
      enabled = alarm.weekdays.isNotEmpty(), triggerAtMillis = null,
    ))
    if (alarm.weekdays.isNotEmpty()) {
      runCatching { enable(alarm) }
        .onFailure { Log.e("AlarmSample", "次回の予約に失敗しました", it) }
    }
    return alarm
  }

  fun restoreOnAppOpen() {
    val alarm = store.read()
    if (!alarm.enabled || !scheduler.canSchedule()) return
    val trigger = alarm.triggerAtMillis
    if (trigger == null) {
      // 次回予約の保存前にプロセスが終了した場合などの復旧。
      restore()
    } else if (trigger > System.currentTimeMillis()) {
      // 日時を再計算せず、保存した今回分と同じ予約を登録する。
      scheduler.schedule(alarm)
    }
    // 過去でも未消費なら、遅れて届く今回分の配信を待つ。
  }

  fun restore() {
    val alarm = store.read()
    if (!alarm.enabled || !scheduler.canSchedule()) return
    if (alarm.weekdays.isNotEmpty()) {
      enable(alarm)
    } else {
      val trigger = alarm.triggerAtMillis
      val now = ZonedDateTime.now().toInstant().toEpochMilli()
      if (trigger == null || trigger <= now) {
        // 電源OFF中に過ぎた単発アラームを翌日に復活させない。
        disable()
      } else {
        scheduler.schedule(alarm)
      }
    }
  }
}
