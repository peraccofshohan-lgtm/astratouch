package com.astratouch.app.domain.model

/** The five themes required by Part 12. */
enum class ThemePreset(
    val id: String,
    val label: String,
    val background: Long,
    val surface: Long,
    val primary: Long,
    val secondary: Long,
    val tertiary: Long,
    val onBackground: Long,
    val isDark: Boolean,
) {
    MIDNIGHT_OBSIDIAN(
        id = "midnight_obsidian",
        label = "Midnight Obsidian",
        background = 0xFF06070C,
        surface = 0xFF101320,
        primary = 0xFF32E6E2,
        secondary = 0xFF7C5CFF,
        tertiary = 0xFFFF4D9D,
        onBackground = 0xFFE8ECF8,
        isDark = true,
    ),
    CYBER_CYAN(
        id = "cyber_cyan",
        label = "Cyber Cyan",
        background = 0xFF03121A,
        surface = 0xFF0A2230,
        primary = 0xFF00F0FF,
        secondary = 0xFF00B8D4,
        tertiary = 0xFF64FFDA,
        onBackground = 0xFFE0FBFF,
        isDark = true,
    ),
    VIOLET_NEBULA(
        id = "violet_nebula",
        label = "Violet Nebula",
        background = 0xFF0B0616,
        surface = 0xFF1A1030,
        primary = 0xFFB388FF,
        secondary = 0xFF8C6BFF,
        tertiary = 0xFFFF7BE5,
        onBackground = 0xFFF0E9FF,
        isDark = true,
    ),
    ARCTIC_GLASS(
        id = "arctic_glass",
        label = "Arctic Glass",
        background = 0xFFEFF4FA,
        surface = 0xFFFFFFFF,
        primary = 0xFF0F6FE0,
        secondary = 0xFF4FC3F7,
        tertiary = 0xFF7E57C2,
        onBackground = 0xFF0A1622,
        isDark = false,
    ),
    CUSTOM(
        id = "custom",
        label = "Custom Theme",
        background = 0xFF06070C,
        surface = 0xFF101320,
        primary = 0xFF32E6E2,
        secondary = 0xFF7C5CFF,
        tertiary = 0xFFFF4D9D,
        onBackground = 0xFFE8ECF8,
        isDark = true,
    ),
    ;

    companion object {
        fun fromId(id: String?): ThemePreset = entries.firstOrNull { it.id == id } ?: MIDNIGHT_OBSIDIAN
    }
}

/** Fully resolved theme row, allowing the Custom preset to be edited. */
data class ThemeConfig(
    val id: String = "theme-default",
    val presetId: String = ThemePreset.MIDNIGHT_OBSIDIAN.id,
    val name: String = ThemePreset.MIDNIGHT_OBSIDIAN.label,
    val background: Long = ThemePreset.MIDNIGHT_OBSIDIAN.background,
    val surface: Long = ThemePreset.MIDNIGHT_OBSIDIAN.surface,
    val primary: Long = ThemePreset.MIDNIGHT_OBSIDIAN.primary,
    val secondary: Long = ThemePreset.MIDNIGHT_OBSIDIAN.secondary,
    val tertiary: Long = ThemePreset.MIDNIGHT_OBSIDIAN.tertiary,
    val onBackground: Long = ThemePreset.MIDNIGHT_OBSIDIAN.onBackground,
    val isDark: Boolean = true,
    val cornerRadiusDp: Int = 22,
    val typographyScale: Float = 1.0f,
    val animationSpeed: Float = 1.0f,
    val hapticIntensity: Float = 1.0f,
    val useMonospace: Boolean = false,
) {
    fun toPreset(): ThemePreset = ThemePreset.fromId(presetId)

    companion object {
        fun fromPreset(preset: ThemePreset): ThemeConfig = ThemeConfig(
            presetId = preset.id,
            name = preset.label,
            background = preset.background,
            surface = preset.surface,
            primary = preset.primary,
            secondary = preset.secondary,
            tertiary = preset.tertiary,
            onBackground = preset.onBackground,
            isDark = preset.isDark,
        )
    }
}
