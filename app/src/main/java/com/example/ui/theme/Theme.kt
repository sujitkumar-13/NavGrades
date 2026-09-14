package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
  primary = NavPrimary,
  onPrimary = Color.White,
  primaryContainer = PrimaryContainer,
  onPrimaryContainer = OnPrimaryContainer,
  secondary = NavSecondary,
  onSecondary = Color.White,
  background = BackgroundDark,
  surface = SurfaceDark,
  surfaceVariant = SurfaceVariantDark,
  outline = OutlineDark,
  onBackground = TextPrimaryDark,
  onSurface = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
  primary = NavPrimary,
  onPrimary = Color.White,
  primaryContainer = PrimaryContainer,
  onPrimaryContainer = OnPrimaryContainer,
  secondary = NavSecondary,
  onSecondary = Color.White,
  secondaryContainer = SecondaryContainer,
  onSecondaryContainer = OnSecondaryContainer,
  background = BackgroundLight,
  surface = SurfaceLight,
  surfaceVariant = SurfaceVariantLight,
  outline = OutlineLight,
  onBackground = TextPrimaryLight,
  onSurface = TextPrimaryLight
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Use the NavGurukul dashboard theme as default
  dynamicColor: Boolean = false, // Keep consistent NavGurukul branding colors
  content: @Composable () -> Unit
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      window.statusBarColor = if (!darkTheme) NavbarBackground.toArgb() else colorScheme.background.toArgb()
      window.navigationBarColor = colorScheme.surface.toArgb()
      val insetsController = WindowCompat.getInsetsController(window, view)
      insetsController.isAppearanceLightStatusBars = !darkTheme
      insetsController.isAppearanceLightNavigationBars = !darkTheme
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
