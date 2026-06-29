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
    primary = BentoPrimaryDark,
    secondary = BentoSecondaryDark,
    tertiary = BentoTertiaryDark,
    primaryContainer = BentoPrimaryContainerDark,
    secondaryContainer = BentoSecondaryContainerDark,
    tertiaryContainer = BentoTertiaryContainerDark,
    background = BentoBackgroundDark,
    surface = BentoBackgroundDark,
    surfaceVariant = BentoSurfaceVariantDark,
    outline = BentoOutlineDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BentoPrimary,
    secondary = BentoSecondary,
    tertiary = BentoTertiary,
    primaryContainer = BentoPrimaryContainer,
    secondaryContainer = BentoSecondaryContainer,
    tertiaryContainer = BentoTertiaryContainer,
    background = BentoBackground,
    surface = BentoBackground,
    surfaceVariant = BentoSurfaceVariant,
    outline = BentoOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
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
