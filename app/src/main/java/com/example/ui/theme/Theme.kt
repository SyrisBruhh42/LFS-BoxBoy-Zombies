package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BoxBoyColorScheme = darkColorScheme(
    primary = PhosphorGreen,
    onPrimary = HandheldObsidian,
    primaryContainer = Color(0xFF0E3820),
    onPrimaryContainer = PhosphorGreen,
    secondary = MysteryGold,
    onSecondary = HandheldObsidian,
    secondaryContainer = Color(0xFF3A2E10),
    onSecondaryContainer = MysteryGold,
    tertiary = ZombieCrimson,
    onTertiary = Color.White,
    background = HandheldObsidian,
    onBackground = TextCrisp,
    surface = HandheldChassis,
    onSurface = TextCrisp,
    surfaceVariant = HandheldSurfaceVariant,
    onSurfaceVariant = TextMuted,
    error = ZombieCrimson,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BoxBoyColorScheme,
        typography = Typography,
        content = content
    )
}
