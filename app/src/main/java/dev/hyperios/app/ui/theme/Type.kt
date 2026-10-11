package dev.hyperios.app.ui.theme

/*
 * This project bundles no custom font (no network access to fetch/vendor SF Pro, and it's
 * an Apple-licensed font we couldn't ship anyway), so these styles use the platform default
 * sans-serif with iOS-matching sizes/weights/tracking. Text still reads convincingly as
 * "iOS system text" because size, weight, and letter-spacing carry most of that
 * impression — see docs/UI_DESIGN_NOTES.md for the reasoning.
 */

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object HyperBounceType {
    val largeTitle = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.36.sp
    )
    val title1 = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.34.sp
    )
    val title2 = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.26.sp
    )
    val title3 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.24.sp
    )
    val headline = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    )
    val body = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp
    )
    val callout = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.18.sp
    )
    val subhead = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp
    )
    val footnote = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp
    )
    val caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    )
    val monoValue = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    )
}
