package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = StreamLiveGreen,
    onPrimary = Color.Black,
    secondary = StreamElectricBlue,
    onSecondary = Color.Black,
    tertiary = StreamAmber,
    error = StreamLiveRed,
    onError = Color.White,
    background = StreamDarkBackground,
    onBackground = StreamTextPrimary,
    surface = StreamDarkSurface,
    onSurface = StreamTextPrimary,
    surfaceVariant = StreamDarkSurfaceVariant,
    onSurfaceVariant = StreamTextSecondary,
    outline = StreamDarkCardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force modern dark studio dashboard
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
