package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class AccessibilityThemeState(
    val highContrastMode: Boolean = false,
    val largeTouchTargetsAndText: Boolean = false,
    val colorblindHazardMarkers: Boolean = false,
    val reduceMotionAndFlashes: Boolean = false,
    val screenReaderAnnouncements: Boolean = true
) {
    fun toThemeConfig(): ThemeConfig = ThemeConfig(
        highContrastMode = highContrastMode,
        largeTouchTargetsAndText = largeTouchTargetsAndText,
        colorblindHazardMarkers = colorblindHazardMarkers,
        reduceMotionAndFlashes = reduceMotionAndFlashes,
        screenReaderAnnouncements = screenReaderAnnouncements,
        textScaleFactor = if (largeTouchTargetsAndText || highContrastMode) 1.25f else 1.0f
    )
}

/**
 * Accessibility and High-Visibility Theme Configuration (`ThemeConfig`) that scales text sizes,
 * activates the high-visibility WCAG AA/AAA color palette, and enforces minimum touch targets
 * and contrast ratios for visually impaired players.
 */
data class ThemeConfig(
    val highContrastMode: Boolean = false,
    val largeTouchTargetsAndText: Boolean = false,
    val colorblindHazardMarkers: Boolean = false,
    val reduceMotionAndFlashes: Boolean = false,
    val screenReaderAnnouncements: Boolean = true,
    val textScaleFactor: Float = if (largeTouchTargetsAndText || highContrastMode) 1.25f else 1.0f
) {
    val minTouchTargetDp: Dp
        get() = if (largeTouchTargetsAndText || highContrastMode) 56.dp else 48.dp

    val colorScheme: ColorScheme
        get() = if (highContrastMode) BoxBoyHighContrastColorScheme else BoxBoyColorScheme

    val scaledTypography: Typography
        get() = buildScaledTypography(if (largeTouchTargetsAndText) max(textScaleFactor, 1.22f) else textScaleFactor)

    /**
     * Computes the WCAG 2.1 contrast ratio between the active primary interactive foreground
     * and background colors (range 1.0 to 21.0).
     */
    val primaryContrastRatio: Double
        get() = calculateContrastRatio(colorScheme.primary, colorScheme.background)

    /**
     * Computes the WCAG 2.1 contrast ratio between surface text and surface background.
     */
    val onSurfaceContrastRatio: Double
        get() = calculateContrastRatio(colorScheme.onSurface, colorScheme.surface)

    /**
     * Verifies that all primary and surface interactive elements meet or exceed WCAG 2.1 Level AA (4.5:1).
     */
    val meetsWcagAA: Boolean
        get() = primaryContrastRatio >= 4.5 &&
            onSurfaceContrastRatio >= 4.5 &&
            calculateContrastRatio(colorScheme.secondary, colorScheme.background) >= 4.5

    /**
     * Verifies whether the active palette meets WCAG 2.1 Level AAA enhanced contrast (7.0:1).
     */
    val meetsWcagAAA: Boolean
        get() = primaryContrastRatio >= 7.0 &&
            onSurfaceContrastRatio >= 7.0 &&
            calculateContrastRatio(colorScheme.secondary, colorScheme.background) >= 7.0

    fun toAccessibilityThemeState(): AccessibilityThemeState = AccessibilityThemeState(
        highContrastMode = highContrastMode,
        largeTouchTargetsAndText = largeTouchTargetsAndText,
        colorblindHazardMarkers = colorblindHazardMarkers,
        reduceMotionAndFlashes = reduceMotionAndFlashes,
        screenReaderAnnouncements = screenReaderAnnouncements
    )

    companion object {
        private fun channelLuminance(channel: Float): Double {
            val c = channel.toDouble().coerceIn(0.0, 1.0)
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        /**
         * Computes WCAG 2.1 relative luminance for an sRGB [Color].
         */
        fun relativeLuminance(color: Color): Double {
            val r = channelLuminance(color.red)
            val g = channelLuminance(color.green)
            val b = channelLuminance(color.blue)
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        /**
         * Computes WCAG 2.1 contrast ratio `(L1 + 0.05) / (L2 + 0.05)` between two colors.
         */
        fun calculateContrastRatio(foreground: Color, background: Color): Double {
            val l1 = relativeLuminance(foreground)
            val l2 = relativeLuminance(background)
            val lighter = max(l1, l2)
            val darker = min(l1, l2)
            return (lighter + 0.05) / (darker + 0.05)
        }

        fun meetsWcagAA(foreground: Color, background: Color, isLargeText: Boolean = false): Boolean {
            val threshold = if (isLargeText) 3.0 else 4.5
            return calculateContrastRatio(foreground, background) >= threshold
        }
    }
}

val LocalAccessibilityTheme = staticCompositionLocalOf { AccessibilityThemeState() }
val LocalThemeConfig = staticCompositionLocalOf { ThemeConfig() }

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

private val BoxBoyHighContrastColorScheme = darkColorScheme(
    primary = HighContrastYellow,
    onPrimary = HighContrastBlack,
    primaryContainer = HighContrastBlack,
    onPrimaryContainer = HighContrastYellow,
    secondary = HighContrastCyan,
    onSecondary = HighContrastBlack,
    secondaryContainer = HighContrastBlack,
    onSecondaryContainer = HighContrastCyan,
    tertiary = HighContrastCrimson,
    onTertiary = HighContrastWhite,
    background = HighContrastBlack,
    onBackground = HighContrastWhite,
    surface = HighContrastBlack,
    onSurface = HighContrastWhite,
    surfaceVariant = HighContrastSurface,
    onSurfaceVariant = HighContrastYellow,
    outline = HighContrastWhite,
    error = HighContrastCrimson,
    onError = HighContrastBlack
)

fun buildScaledTypography(scale: Float): Typography {
    val clamped = scale.coerceIn(0.85f, 1.6f)
    if (clamped == 1.0f) return Typography
    return Typography(
        displayLarge = Typography.displayLarge.copy(
            fontSize = (Typography.displayLarge.fontSize.value * clamped).sp,
            lineHeight = (Typography.displayLarge.lineHeight.value * clamped).sp
        ),
        headlineMedium = Typography.headlineMedium.copy(
            fontSize = (Typography.headlineMedium.fontSize.value * clamped).sp,
            lineHeight = (Typography.headlineMedium.lineHeight.value * clamped).sp
        ),
        titleLarge = Typography.titleLarge.copy(
            fontSize = (Typography.titleLarge.fontSize.value * clamped).sp,
            lineHeight = (Typography.titleLarge.lineHeight.value * clamped).sp
        ),
        titleMedium = Typography.titleMedium.copy(
            fontSize = (Typography.titleMedium.fontSize.value * clamped).sp,
            lineHeight = (Typography.titleMedium.lineHeight.value * clamped).sp
        ),
        bodyLarge = Typography.bodyLarge.copy(
            fontSize = (Typography.bodyLarge.fontSize.value * clamped).sp,
            lineHeight = (Typography.bodyLarge.lineHeight.value * clamped).sp
        ),
        bodyMedium = Typography.bodyMedium.copy(
            fontSize = (Typography.bodyMedium.fontSize.value * clamped).sp,
            lineHeight = (Typography.bodyMedium.lineHeight.value * clamped).sp
        ),
        labelLarge = Typography.labelLarge.copy(
            fontSize = (Typography.labelLarge.fontSize.value * clamped).sp,
            lineHeight = (Typography.labelLarge.lineHeight.value * clamped).sp
        ),
        labelSmall = Typography.labelSmall.copy(
            fontSize = (Typography.labelSmall.fontSize.value * clamped).sp,
            lineHeight = (Typography.labelSmall.lineHeight.value * clamped).sp
        )
    )
}

@Composable
fun ThemeConfigProvider(
    themeConfig: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit
) {
    val accessibilityState = themeConfig.toAccessibilityThemeState()
    CompositionLocalProvider(
        LocalThemeConfig provides themeConfig,
        LocalAccessibilityTheme provides accessibilityState
    ) {
        MaterialTheme(
            colorScheme = themeConfig.colorScheme,
            typography = themeConfig.scaledTypography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    accessibilityState: AccessibilityThemeState = AccessibilityThemeState(),
    content: @Composable () -> Unit
) {
    ThemeConfigProvider(
        themeConfig = accessibilityState.toThemeConfig(),
        content = content
    )
}

