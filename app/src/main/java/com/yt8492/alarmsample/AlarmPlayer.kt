package com.yt8492.alarmsample

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

class AlarmPlayer(
  context: Context,
  private val onFailure: (String) -> Unit,
) {
  private val context = context.applicationContext
  private val audioManager = context.getSystemService(AudioManager::class.java)
  private val handler = Handler(Looper.getMainLooper())
  private val attributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()
  private val focusRequest = AudioFocusRequest.Builder(
    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
  )
    .setAudioAttributes(attributes)
    .setAcceptsDelayedFocusGain(true)
    .setWillPauseWhenDucked(true)
    .setOnAudioFocusChangeListener(
      { change -> onFocusChanged(change) }, handler,
    )
    .build()

  private var player: MediaPlayer? = null
  private var prepared = false
  private var hasFocus = false
  private var active = false
  private val sources = ArrayDeque<Uri>()

  fun start(soundFileName: String?) {
    stop()
    active = true
    sources.clear()
    SoundStore(context).resolve(soundFileName)?.let {
      sources.add(Uri.fromFile(it))
    }
    RingtoneManager.getDefaultUri(
      RingtoneManager.TYPE_ALARM,
    )?.let(sources::add)
    val fallback = "android.resource://${context.packageName}/" +
      R.raw.fallback_alarm
    sources.add(Uri.parse(fallback))

    when (audioManager.requestAudioFocus(focusRequest)) {
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> hasFocus = true
      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> hasFocus = false
      else -> {
        active = false
        onFailure("音声フォーカスを取得できませんでした")
        return
      }
    }
    prepareNext()
  }

  private fun prepareNext() {
    releasePlayer()
    if (!active) return
    val uri = sources.removeFirstOrNull()
    if (uri == null) {
      onFailure("音源を再生できませんでした")
      return
    }
    val next = MediaPlayer()
    player = next
    try {
      next.setAudioAttributes(attributes)
      next.setDataSource(context, uri)
      next.isLooping = true
      next.setOnPreparedListener { ready ->
        if (player === ready && active) {
          prepared = true
          if (hasFocus) ready.start()
        }
      }
      next.setOnErrorListener { failed, _, _ ->
        // コールバックを抜けてから、次の音源へ切り替える。
        handler.post { if (player === failed && active) prepareNext() }
        true
      }
      next.prepareAsync()
    } catch (_: Exception) {
      handler.post { if (player === next && active) prepareNext() }
    }
  }

  private fun onFocusChanged(change: Int) {
    if (!active) return
    when (change) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        hasFocus = true
        if (prepared) player?.start()
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        hasFocus = false
        if (prepared && player?.isPlaying == true) player?.pause()
      }
      AudioManager.AUDIOFOCUS_LOSS -> {
        stop()
        onFailure("他の音声再生に切り替わったため、アラームを停止しました")
      }
    }
  }

  fun stop() {
    active = false
    hasFocus = false
    handler.removeCallbacksAndMessages(null)
    releasePlayer()
    audioManager.abandonAudioFocusRequest(focusRequest)
  }

  private fun releasePlayer() {
    prepared = false
    player?.run {
      setOnPreparedListener(null)
      setOnErrorListener(null)
      // releaseは準備中やエラー状態でも呼べるため、先にstopする必要はない。
      release()
    }
    player = null
  }
}
