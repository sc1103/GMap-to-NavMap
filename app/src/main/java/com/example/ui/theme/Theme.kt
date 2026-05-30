package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SlateGreen80,
    secondary = EmeraldGrey80,
    tertiary = SoftCyan80,
    background = Color(0xFF111E18),
    surface = Color(0xFF16251E),
    onPrimary = Color(0xFF0F261B),
    onSecondary = Color(0xFF152A1F),
    onBackground = Color(0xFFE1F5FE),
    onSurface = Color(0xFFE1F5FE)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SlateGreen40,
    secondary = EmeraldGrey40,
    tertiary = SoftCyan40,
    background = Color(0xFFFAFCFA),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF131F19),
    onSurface = Color(0xFF131F19)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // Set false to prioritize our gorgeous thematic palette
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
