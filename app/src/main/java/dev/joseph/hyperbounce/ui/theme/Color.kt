package dev.joseph.hyperbounce.ui.theme

/*
 * Color tokens mirroring iOS's system color palette and semantic naming
 * (label/secondaryLabel/systemBackground/groupedBackground/separator/systemBlue, etc.),
 * light and dark variants. These are plain values, not a MaterialTheme — this app
 * deliberately has no Material3 dependency, since every surface is built from the
 * AndroidLiquidGlass backdrop primitives instead of Material components.
 */

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class HyperBounceColors(
    val isDark: Boolean,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val background: Color,
    val elevatedSurfaceTint: Color,
    val separator: Color,
    val accentBlue: Color,
    val accentGreen: Color,
    val accentOrange: Color,
    val accentRed: Color,
    val wallpaperTop: Color,
    val wallpaperBottom: Color,
    val glassSurfaceTint: Color
)

private val LightColors = HyperBounceColors(
    isDark = false,
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    background = Color(0xFFF2F2F7),
    elevatedSurfaceTint = Color(0xFFFFFFFF),
    separator = Color(0x4A3C3C43),
    accentBlue = Color(0xFF0088FF),
    accentGreen = Color(0xFF34C759),
    accentOrange = Color(0xFFFF9500),
    accentRed = Color(0xFFFF3B30),
    wallpaperTop = Color(0xFF7FB2E5),
    wallpaperBottom = Color(0xFFE8C9A8),
    glassSurfaceTint = Color.White
)

private val DarkColors = HyperBounceColors(
    isDark = true,
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    background = Color(0xFF000000),
    elevatedSurfaceTint = Color(0xFF1C1C1E),
    separator = Color(0x4A545458),
    accentBlue = Color(0xFF0091FF),
    accentGreen = Color(0xFF30D158),
    accentOrange = Color(0xFFFF9F0A),
    accentRed = Color(0xFFFF453A),
    wallpaperTop = Color(0xFF141A33),
    wallpaperBottom = Color(0xFF2B1F3D),
    glassSurfaceTint = Color(0xFF1C1C1E)
)

@Composable
fun hyperBounceColors(): HyperBounceColors =
    if (isSystemInDarkTheme()) DarkColors else LightColors
