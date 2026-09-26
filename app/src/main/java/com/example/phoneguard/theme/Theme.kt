package com.example.phoneguard.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ChildColorScheme =
  lightColorScheme(
    primary = ChildAccent,
    onPrimary = ChildLight,
    primaryContainer = ChildAccentSoft,
    onPrimaryContainer = ChildDark,
    secondary = ChildDark,
    onSecondary = ChildLight,
    secondaryContainer = ChildSurfaceMuted,
    onSecondaryContainer = ChildDark,
    tertiary = ChildTextSecondary,
    onTertiary = ChildLight,
    tertiaryContainer = ChildSurfaceMuted,
    onTertiaryContainer = ChildDark,
    background = ChildLight,
    onBackground = ChildDark,
    surface = ChildLight,
    onSurface = ChildDark,
    surfaceVariant = ChildSurfaceMuted,
    onSurfaceVariant = ChildTextSecondary,
    outline = ChildOutline,
    outlineVariant = ChildOutlineSoft,
    error = ChildAccentDark,
    onError = Color.White,
    errorContainer = ChildAccentSoft,
    onErrorContainer = ChildDark,
  )

@Composable
fun PhoneGuardTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = ChildColorScheme,
    typography = Typography,
    content = content,
  )
}
