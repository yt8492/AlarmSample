package com.yt8492.alarmsample

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AlarmColors = lightColorScheme(
  primary = Color(0xFF2656A3),
  background = Color(0xFFF7F8FC),
  surface = Color(0xFFF7F8FC),
  onBackground = Color(0xFF18263F),
  onSurface = Color(0xFF18263F),
)

internal object AlarmSpacing {
  val screen = 20.dp
  val section = 20.dp
  val item = 8.dp
  val stopButton = 64.dp
}

@Composable
fun AlarmSampleTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = AlarmColors, content = content)
}
