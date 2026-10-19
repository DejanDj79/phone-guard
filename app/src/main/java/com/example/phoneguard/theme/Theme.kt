package com.example.phoneguard.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ChildColorScheme =
  lightColorScheme(
    primary = ChildAccent,
    onPrimary = ChildDark,
    primaryContainer = ChildAccentSoft,
    onPrimaryContainer = ChildLight,
    secondary = ChildAccent,
    onSecondary = ChildDark,
    secondaryContainer = ChildSurface,
    onSecondaryContainer = ChildLight,
    tertiary = ChildAccent,
    onTertiary = ChildDark,
    tertiaryContainer = ChildSurfaceMuted,
    onTertiaryContainer = ChildLight,
    background = ChildBackground,
    onBackground = ChildLight,
    surface = ChildSurface,
    onSurface = ChildLight,
    surfaceVariant = ChildSurfaceMuted,
    onSurfaceVariant = ChildTextSecondary,
    surfaceBright = ChildSurface,
    surfaceDim = ChildBackground,
    surfaceContainerLowest = ChildBackground,
    surfaceContainerLow = ChildSurfaceMuted,
    surfaceContainer = ChildSurfaceMuted,
    surfaceContainerHigh = ChildSurface,
    surfaceContainerHighest = ChildSurface,
    inverseSurface = ChildDark,
    inverseOnSurface = ChildLight,
    inversePrimary = ChildAccent,
    outline = ChildOutline,
    outlineVariant = ChildOutlineSoft,
    error = ChildAccent,
    onError = ChildDark,
    errorContainer = ChildSurfaceMuted,
    onErrorContainer = ChildLight,
    scrim = ChildDark,
  )

private val ChildShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
    shapes = ChildShapes,
    content = content,
  )
}
