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
    primary = Color(0xFF155B63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDECF0),
    onPrimaryContainer = Color(0xFF0D3438),
    secondary = Color(0xFF356A70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDF5F7),
    onSecondaryContainer = Color(0xFF173F43),
    tertiary = Color(0xFF52664E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE9D8),
    onTertiaryContainer = Color(0xFF243421),
    background = Color(0xFF88D9E0),
    onBackground = Color(0xFF15373A),
    surface = Color(0xFFF8FDFD),
    onSurface = Color(0xFF17373A),
    surfaceVariant = Color(0xFFD7EEF0),
    onSurfaceVariant = Color(0xFF52686A),
    outline = Color(0xFF759194),
    outlineVariant = Color(0xFFB9D6D8),
    error = Color(0xFF9A3F3B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410006),
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.35).sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Normal,
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
