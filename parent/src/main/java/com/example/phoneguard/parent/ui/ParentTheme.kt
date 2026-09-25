package com.example.phoneguard.parent.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val RoundedFamily =
  FontFamily(
    Font(DeviceFontFamilyName("sans-serif-rounded"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-rounded"), FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif"), FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-rounded"), FontWeight.SemiBold),
    Font(DeviceFontFamilyName("sans-serif"), FontWeight.SemiBold),
    Font(DeviceFontFamilyName("sans-serif-rounded"), FontWeight.Bold),
    Font(DeviceFontFamilyName("sans-serif"), FontWeight.Bold),
  )

private val PlainFamily = FontFamily.SansSerif

private val ParentColors =
  lightColorScheme(
    primary = Color(0xFF6F5B00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE783),
    onPrimaryContainer = Color(0xFF251E00),
    secondary = Color(0xFF776A27),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFEDAA),
    onSecondaryContainer = Color(0xFF282100),
    tertiary = Color(0xFF53684E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5E8CE),
    onTertiaryContainer = Color(0xFF132012),
    background = Color(0xFFFFF7D6),
    onBackground = Color(0xFF221F13),
    surface = Color(0xFFFFFBEB),
    onSurface = Color(0xFF221F13),
    surfaceVariant = Color(0xFFF2E9C5),
    onSurfaceVariant = Color(0xFF625B42),
    outline = Color(0xFF9A906D),
    outlineVariant = Color(0xFFD9CFAB),
    error = Color(0xFF9F403A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF3F0505),
  )

private val ParentTypography =
  Typography(
    displaySmall =
      TextStyle(
        fontFamily = RoundedFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = RoundedFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.5).sp,
      ),
    headlineSmall =
      TextStyle(
        fontFamily = RoundedFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.35).sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = RoundedFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = RoundedFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = RoundedFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = RoundedFamily,
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
