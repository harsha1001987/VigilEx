package com.extrive.vigilex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val VigilExColors = lightColorScheme(
    primary = Yellow,
    onPrimary = Black,
    primaryContainer = Yellow,
    onPrimaryContainer = Black,
    secondary = Charcoal,
    onSecondary = White,
    tertiary = Gold,
    onTertiary = Black,
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceSunken,
    onSurfaceVariant = InkSecondary,
    surfaceContainerHigh = Surface,
    outline = HairlineStrong,
    outlineVariant = Hairline,
    error = Red,
    onError = White
)

@Composable
fun VigilExTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VigilExColors,
        typography = Typography,
        content = content
    )
}
