package com.example.phoneguard.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.phoneguard.R

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

val Typography =
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
