package com.example.phoneguard.parent.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

internal val ParentAccentColor = Color(0xFF69DEFF)
internal val ParentBorderColor = Color(0xFF7382A3)
internal val ParentHeaderIconColor = Color(0xFF95A4C5)
internal val ParentActionShape = RoundedCornerShape(12.dp)
internal val ParentGradientEdge = Color(0xFF3E4759)
internal val ParentGradientCenter = Color(0xFF5C6B8A)

private val ParentColors =
  lightColorScheme(
    primary = Color(0xFF69DEFF),
    onPrimary = Color(0xFF3E4759),
    primaryContainer = Color(0xFF8194B5),
    onPrimaryContainer = Color(0xFFEFF6FF),
    secondary = Color(0xFF69DEFF),
    onSecondary = Color(0xFF3E4759),
    secondaryContainer = Color(0xFF8194B5),
    onSecondaryContainer = Color(0xFFEFF6FF),
    tertiary = Color(0xFF69DEFF),
    onTertiary = Color(0xFF3E4759),
    tertiaryContainer = Color(0xFF8194B5),
    onTertiaryContainer = Color(0xFFEFF6FF),
    background = Color(0xFF5C6B8A),
    onBackground = Color(0xFFEFF6FF),
    surface = Color(0xFF8194B5),
    onSurface = Color(0xFFEFF6FF),
    surfaceVariant = Color(0xFF7183A3),
    onSurfaceVariant = Color(0xFFEFF6FF),
    outline = ParentBorderColor,
    outlineVariant = ParentBorderColor,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C4A52),
    onErrorContainer = Color(0xFFFFDAD6),
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
