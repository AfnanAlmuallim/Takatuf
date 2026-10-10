package com.z.takatuf.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.z.takatuf.R

/**
 * Alexandria (display) is a geometric face with Kufi roots, echoing the logo's Arabic
 * wordmark. IBM Plex Sans Arabic (text) carries body copy and controls. Both cover Arabic
 * and Latin, so an Arabic locale needs no font changes.
 *
 * Sizes follow the classical scale: 11, 12, 14, 16, 18, 21, 24, 36, 48.
 */
val Alexandria = FontFamily(
    Font(R.font.alexandria_medium, FontWeight.Medium),
    Font(R.font.alexandria_semibold, FontWeight.SemiBold),
    Font(R.font.alexandria_bold, FontWeight.Bold),
)

val PlexSans = FontFamily(
    Font(R.font.plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_sans_arabic_semibold, FontWeight.SemiBold),
)

private val CenteredLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun display(size: Int, line: Int, weight: FontWeight, tracking: TextUnit = 0.sp) = TextStyle(
    fontFamily = Alexandria,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking,
    lineHeightStyle = CenteredLines,
)

private fun text(size: Int, line: Int, weight: FontWeight, tracking: TextUnit = 0.sp) = TextStyle(
    fontFamily = PlexSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking,
    lineHeightStyle = CenteredLines,
)

val TakatufTypography = Typography(
    displayLarge = display(48, 52, FontWeight.Bold, (-0.8).sp),
    displayMedium = display(36, 42, FontWeight.Bold, (-0.5).sp),
    displaySmall = display(36, 42, FontWeight.SemiBold, (-0.4).sp),
    headlineLarge = display(24, 30, FontWeight.Bold, (-0.3).sp),
    headlineMedium = display(24, 30, FontWeight.SemiBold, (-0.2).sp),
    headlineSmall = display(21, 28, FontWeight.SemiBold, (-0.1).sp),
    titleLarge = display(21, 28, FontWeight.SemiBold, (-0.1).sp),
    titleMedium = display(18, 24, FontWeight.SemiBold),
    titleSmall = display(16, 22, FontWeight.Medium),
    bodyLarge = text(16, 24, FontWeight.Normal),
    bodyMedium = text(14, 20, FontWeight.Normal),
    bodySmall = text(12, 16, FontWeight.Normal, 0.1.sp),
    labelLarge = text(14, 20, FontWeight.SemiBold, 0.1.sp),
    labelMedium = text(12, 16, FontWeight.Medium, 0.2.sp),
    labelSmall = text(11, 16, FontWeight.Medium, 0.3.sp),
)
