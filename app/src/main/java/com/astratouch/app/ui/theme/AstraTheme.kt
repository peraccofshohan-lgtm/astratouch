package com.astratouch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astratouch.app.domain.model.ThemeConfig
import com.astratouch.app.domain.model.ThemePreset

fun Long.toColor(): Color = Color(this.toInt())

/** Astra-specific design tokens that are not part of Material 3. */
data class AstraColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val glow: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val outline: Color,
)

val LocalAstraColors = staticCompositionLocalOf {
    AstraColors(
        primary = ThemePreset.MIDNIGHT_OBSIDIAN.primary.toColor(),
        secondary = ThemePreset.MIDNIGHT_OBSIDIAN.secondary.toColor(),
        tertiary = ThemePreset.MIDNIGHT_OBSIDIAN.tertiary.toColor(),
        background = ThemePreset.MIDNIGHT_OBSIDIAN.background.toColor(),
        surface = ThemePreset.MIDNIGHT_OBSIDIAN.surface.toColor(),
        onBackground = ThemePreset.MIDNIGHT_OBSIDIAN.onBackground.toColor(),
        glow = Color(0x3332E6E2),
        success = Color(0xFF34D399),
        warning = Color(0xFFFBBF24),
        danger = Color(0xFFF87171),
        outline = Color(0x33FFFFFF),
    )
}

private fun ThemeConfig.toAstraColors(): AstraColors {
    val dark = isDark
    return AstraColors(
        primary = primary.toColor(),
        secondary = secondary.toColor(),
        tertiary = tertiary.toColor(),
        background = background.toColor(),
        surface = surface.toColor(),
        onBackground = onBackground.toColor(),
        glow = primary.toColor().copy(alpha = if (dark) 0.28f else 0.16f),
        success = Color(0xFF34D399),
        warning = Color(0xFFFBBF24),
        danger = Color(0xFFF87171),
        outline = if (dark) Color(0x26FFFFFF) else Color(0x1A000000),
    )
}

fun astraTypography(scale: Float = 1f, monospace: Boolean = false): Typography {
    val family = if (monospace) FontFamily.Monospace else FontFamily.SansSerif
    fun style(size: Int, weight: FontWeight, spacing: Int = 0, lineHeight: Int = 0) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        letterSpacing = spacing.sp,
        lineHeight = (if (lineHeight == 0) (size * 1.35f) else lineHeight.toFloat()).times(scale).sp,
    )
    return Typography(
        displaySmall = style(32, FontWeight.SemiBold, lineHeight = 38),
        headlineMedium = style(26, FontWeight.SemiBold, lineHeight = 32),
        headlineSmall = style(22, FontWeight.SemiBold, lineHeight = 28),
        titleLarge = style(20, FontWeight.SemiBold, lineHeight = 26),
        titleMedium = style(16, FontWeight.Medium, lineHeight = 22),
        titleSmall = style(14, FontWeight.Medium, lineHeight = 20),
        bodyLarge = style(16, FontWeight.Normal, lineHeight = 24),
        bodyMedium = style(14, FontWeight.Normal, lineHeight = 20),
        bodySmall = style(12, FontWeight.Normal, lineHeight = 16),
        labelLarge = style(14, FontWeight.Medium, lineHeight = 18),
        labelMedium = style(12, FontWeight.Medium, lineHeight = 16),
        labelSmall = style(11, FontWeight.Medium, lineHeight = 14),
    )
}

@Composable
fun AstraTheme(
    theme: ThemeConfig = ThemeConfig.fromPreset(ThemePreset.MIDNIGHT_OBSIDIAN),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val astra = theme.toAstraColors()
    val scheme = if (theme.isDark || isSystemInDarkTheme() && theme.toPreset().isDark) {
        darkColorScheme(
            primary = astra.primary,
            onPrimary = Color(0xFF04060B),
            primaryContainer = astra.primary.copy(alpha = 0.18f),
            onPrimaryContainer = astra.primary,
            secondary = astra.secondary,
            onSecondary = Color(0xFF04060B),
            secondaryContainer = astra.secondary.copy(alpha = 0.18f),
            tertiary = astra.tertiary,
            background = astra.background,
            onBackground = astra.onBackground,
            surface = astra.surface,
            onSurface = astra.onBackground,
            surfaceVariant = astra.surface.copy(alpha = 0.9f),
            onSurfaceVariant = astra.onBackground.copy(alpha = 0.72f),
            outline = astra.outline,
            error = astra.danger,
        )
    } else {
        lightColorScheme(
            primary = astra.primary,
            onPrimary = Color.White,
            primaryContainer = astra.primary.copy(alpha = 0.14f),
            onPrimaryContainer = astra.primary,
            secondary = astra.secondary,
            background = astra.background,
            onBackground = astra.onBackground,
            surface = astra.surface,
            onSurface = astra.onBackground,
            surfaceVariant = astra.surface,
            onSurfaceVariant = astra.onBackground.copy(alpha = 0.7f),
            outline = astra.outline,
            error = astra.danger,
        )
    }

    CompositionLocalProvider(LocalAstraColors provides astra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = astraTypography(
                scale = theme.typographyScale * if (reducedMotion) 1f else 1f,
                monospace = theme.useMonospace,
            ),
            shapes = androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(theme.cornerRadiusDp.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape((theme.cornerRadiusDp + 8).dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape((theme.cornerRadiusDp + 14).dp),
            ),
            content = content,
        )
    }
}
