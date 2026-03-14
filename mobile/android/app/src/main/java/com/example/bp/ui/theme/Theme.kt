package com.example.bp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary = BPBlue,
    onPrimary = TextPrimary,
    primaryContainer = BPBlueDark,
    secondary = AccentCyan,
    onSecondary = TextPrimary,
    tertiary = AccentPurple,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    error = BPRed,
    onError = TextPrimary,
    outline = DividerDark
)

@Composable
fun BPTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}