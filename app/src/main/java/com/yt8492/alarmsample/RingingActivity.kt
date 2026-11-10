package com.yt8492.alarmsample

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class RingingActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (!AlarmRingingService.isRinging) {
      finish()
      return
    }
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= 27) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }
    setContent {
      AlarmSampleTheme {
        RingingScreen(onStop = {
          stopService(Intent(this, AlarmRingingService::class.java))
          finish()
        })
      }
    }
  }
}
