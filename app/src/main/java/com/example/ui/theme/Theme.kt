package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PurpleContainer,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = TextDark,
    surface = TextDark,
    onPrimary = OnPurpleContainer,
    onBackground = AppBackground,
    onSurface = AppBackground
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    secondary = PurpleContainer,
    tertiary = OnPurpleContainer,
    background = AppBackground,
    surface = NeutralContainer,
    onPrimary = AppBackground,
    onSecondary = OnPurpleContainer,
    onBackground = TextDark,
    onSurface = TextDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic coloring to enforce the exact "Bold Typography" design theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
