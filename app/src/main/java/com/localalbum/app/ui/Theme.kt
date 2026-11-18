package com.localalbum.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal val Ink = Color(0xFF1C1C1E)
internal val Quiet = Color(0xFF3A3A3C)
internal val Mist = Color(0xFFF2F2F7)
internal val Blue = Color(0xFF007AFF)
internal val LikeRed = Color(0xFFFF3B30)
internal val Line = Color(0xFFE5E5EA)
internal val Field = Color(0xFFF2F2F7)
internal val PhotoWell = Color(0xFF000000)
internal val Sheet = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F2FF),
    onPrimaryContainer = Color(0xFF003D80),
    background = Mist,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Field,
    onSurfaceVariant = Quiet,
    outline = Line,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A325C),
    onPrimaryContainer = Color(0xFFD6E9FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outline = Color(0xFF3A3A3C),
)

private val CjkLine = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun typeStyle(
    size: TextUnit,
    line: TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size,
    lineHeight = line,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = CjkLine,
)

/**
 * System sans for Chinese, with a tighter scale than the old oversized SemiBold set.
 * Titles stay clear at Medium. Meta styles are smaller so they sit quieter than names.
 * Letter spacing stays at zero so Chinese glyphs are not pulled apart.
 */
private val AlbumType = Typography(
    displayLarge = typeStyle(32.sp, 40.sp, FontWeight.Medium),
    displayMedium = typeStyle(28.sp, 36.sp, FontWeight.Medium),
    displaySmall = typeStyle(24.sp, 32.sp, FontWeight.Medium),
    headlineLarge = typeStyle(28.sp, 36.sp, FontWeight.Medium),
    headlineMedium = typeStyle(26.sp, 34.sp, FontWeight.Medium),
    headlineSmall = typeStyle(22.sp, 30.sp, FontWeight.Medium),
    titleLarge = typeStyle(22.sp, 30.sp, FontWeight.Medium),
    titleMedium = typeStyle(18.sp, 26.sp, FontWeight.Medium),
    titleSmall = typeStyle(16.sp, 22.sp, FontWeight.Medium),
    bodyLarge = typeStyle(18.sp, 28.sp),
    bodyMedium = typeStyle(16.sp, 24.sp),
    bodySmall = typeStyle(14.sp, 20.sp),
    labelLarge = typeStyle(18.sp, 24.sp, FontWeight.Medium),
    labelMedium = typeStyle(15.sp, 20.sp, FontWeight.Medium),
    labelSmall = typeStyle(13.sp, 18.sp, FontWeight.Medium),
)

@Composable
fun LocalAlbumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AlbumType,
        content = content,
    )
}
