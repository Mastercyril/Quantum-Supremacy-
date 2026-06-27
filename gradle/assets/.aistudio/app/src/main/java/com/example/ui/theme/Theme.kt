package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CyberCyan,
    secondary = CyberPurple,
    tertiary = CyberGreen,
    background = CyberBlack,
    surface = CyberSurface,
    onBackground = CyberWhite,
    onSurface = CyberWhite,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTealMuted
  )

private val LightColorScheme = DarkColorScheme // Force dark deck aesthetic

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark Cyberpunk Console by default
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve proprietary theme
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
