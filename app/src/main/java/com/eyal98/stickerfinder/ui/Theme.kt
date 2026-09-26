package com.eyal98.stickerfinder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// The app's own colors, from the logo: violet, teal, and a warm amber accent.
private val Violet = Color(0xFF5B4BDB)
private val VioletLight = Color(0xFFC7BFFF)
private val Teal = Color(0xFF00A383)
private val TealLight = Color(0xFF5CDBBE)
private val Amber = Color(0xFFF2A900)
private val AmberLight = Color(0xFFFFD36E)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF1B1164),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F3E6),
    onSecondaryContainer = Color(0xFF00382B),
    tertiary = Amber,
    onTertiary = Color(0xFF2D2A4A),
    tertiaryContainer = Color(0xFFFFEBC2),
    onTertiaryContainer = Color(0xFF3D2A00),
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFE7E4F2),
    onSurfaceVariant = Color(0xFF48455A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F4FC),
    surfaceContainer = Color(0xFFF0EEF8),
    surfaceContainerHigh = Color(0xFFEAE7F4),
    surfaceContainerHighest = Color(0xFFE4E1EF),
    outline = Color(0xFF79768C),
    outlineVariant = Color(0xFFCAC6DA),
)

private val DarkColors = darkColorScheme(
    primary = VioletLight,
    onPrimary = Color(0xFF2A1C8F),
    primaryContainer = Color(0xFF4234B8),
    onPrimaryContainer = Color(0xFFE6E1FF),
    secondary = TealLight,
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF005140),
    onSecondaryContainer = Color(0xFFC8F3E6),
    tertiary = AmberLight,
    onTertiary = Color(0xFF3D2A00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFEBC2),
    background = Color(0xFF131218),
    onBackground = Color(0xFFE5E1EC),
    surface = Color(0xFF131218),
    onSurface = Color(0xFFE5E1EC),
    surfaceVariant = Color(0xFF48455A),
    onSurfaceVariant = Color(0xFFCAC6DA),
    surfaceContainerLowest = Color(0xFF0E0D13),
    surfaceContainerLow = Color(0xFF1C1B22),
    surfaceContainer = Color(0xFF201F27),
    surfaceContainerHigh = Color(0xFF2A2932),
    surfaceContainerHighest = Color(0xFF35343D),
    outline = Color(0xFF938FA6),
    outlineVariant = Color(0xFF48455A),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun StickerFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
