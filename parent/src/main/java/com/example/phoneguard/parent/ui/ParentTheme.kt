package com.example.phoneguard.parent.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.phoneguard.parent.R

private val ManropeFamily =
  FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold),
  )

private val MichromaFamily =
  FontFamily(
    Font(R.font.michroma, FontWeight.Normal),
    Font(R.font.michroma, FontWeight.Medium),
    Font(R.font.michroma, FontWeight.SemiBold),
    Font(R.font.michroma, FontWeight.Bold),
  )

internal val ParentAccentColor = Color(0xFFE83E1D)
internal val ParentGradientEdge = Color(0xFFDEDEDA)
internal val ParentGradientCenter = Color(0xFFF3F3F0)

private val ParentColors =
  lightColorScheme(
    primary = Color(0xFF3C3C3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E8E4),
    onPrimaryContainer = Color(0xFF31312F),
    secondary = Color(0xFF55575A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0F0ED),
    onSecondaryContainer = Color(0xFF333537),
    tertiary = Color(0xFFE68E6C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8E4DB),
    onTertiaryContainer = Color(0xFF5B2B1A),
    background = Color(0xFFECECE8),
    onBackground = Color(0xFF2F3031),
    surface = Color(0xFFF6F6F3),
    onSurface = Color(0xFF303132),
    surfaceVariant = Color(0xFFE9E9E5),
    onSurfaceVariant = Color(0xFF7F8082),
    outline = Color(0xFF8D8D89),
    outlineVariant = Color(0xFFD3D3CE),
    error = Color(0xFFA43D3D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF8DEDE),
    onErrorContainer = Color(0xFF4E1717),
  )

private val ParentTypography =
  Typography(
    displaySmall =
      TextStyle(
        fontFamily = MichromaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.1.sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = MichromaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.2.sp,
      ),
    headlineSmall =
      TextStyle(
        fontFamily = MichromaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.2.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = MichromaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = MichromaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.45.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = MichromaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.45.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = MichromaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = MichromaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.65.sp,
      ),
  )

@Composable
internal fun PhoneGuardParentTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = ParentColors,
    typography = ParentTypography,
    content = content,
  )
}
