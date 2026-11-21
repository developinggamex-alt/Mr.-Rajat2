package com.appnest.store.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AppNestColorScheme = darkColorScheme(
    primary = NestBright,
    onPrimary = NestOnPrimary,
    primaryContainer = NestChipActiveBg,
    onPrimaryContainer = NestChipActiveText,
    secondary = NestAccent,
    onSecondary = NestOnPrimary,
    secondaryContainer = NestNavActiveBg,
    onSecondaryContainer = NestNavActiveText,
    tertiary = NestSuccess,
    onTertiary = NestOnPrimary,
    background = NestBg,
    onBackground = NestText,
    surface = NestSurface,
    onSurface = NestText,
    surfaceVariant = NestPanel,
    onSurfaceVariant = NestMuted,
    surfaceContainerLowest = NestBg,
    surfaceContainerLow = NestSurface,
    surfaceContainer = NestPanel,
    surfaceContainerHigh = NestPanelElevated,
    outline = NestLine,
    outlineVariant = NestLineStrong,
    error = NestError,
    onError = NestOnPrimary,
    errorContainer = NestErrorContainer,
    onErrorContainer = NestText
)

private val AppNestShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun AppNestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppNestColorScheme,
        typography = AppNestTypography,
        shapes = AppNestShapes,
        content = content
    )
}
