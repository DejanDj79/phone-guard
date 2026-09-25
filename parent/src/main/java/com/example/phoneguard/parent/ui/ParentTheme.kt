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

private val PlainFamily = FontFamily.SansSerif

private val ParentColors =
  lightColorScheme(
    primary = Color(0xFF2F4FA3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF192A5A),
    secondary = Color(0xFFD47B94),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7D9E2),
    onSecondaryContainer = Color(0xFF5D2D3D),
    tertiary = Color(0xFF6F7FAE),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E8F5),
    onTertiaryContainer = Color(0xFF293451),
    background = Color(0xFFF4E9E7),
    onBackground = Color(0xFF202333),
    surface = Color(0xFFFFFAF7),
    onSurface = Color(0xFF202331),
    surfaceVariant = Color(0xFFF0E7E5),
    onSurfaceVariant = Color(0xFF6D6265),
    outline = Color(0xFF9B8E91),
    outlineVariant = Color(0xFFDCCFD0),
    error = Color(0xFFA43D4B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD9DF),
    onErrorContainer = Color(0xFF41000B),
  )

private val ParentTypography =
  Typography(
    displaySmall =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.55).sp,
      ),
    headlineSmall =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.35).sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 21.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = PlainFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = PlainFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = PlainFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = PlainFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
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
