package com.image.word.converter.convert.docx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = WordPrimary,
    onPrimary = Color.White,
    secondary = WordSecondary,
    background = LightBackground,
    onBackground = Color(0xFF102018),
    surface = LightSurface,
    onSurface = Color(0xFF102018),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF42574C),
)

private val DarkColorScheme = darkColorScheme(
    primary = WordPrimaryLight,
    onPrimary = Color(0xFF012213),
    secondary = Color(0xFF77A9FF),
    background = DarkBackground,
    onBackground = Color(0xFFE4F0FF),
    surface = DarkSurface,
    onSurface = Color(0xFFE4F0FF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB3C4D9),
)

@Composable
fun ImageToWordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
