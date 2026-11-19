package com.astratouch.app.domain.model

/** The six built-in smart profiles (Part 11) plus user-created ones. */
enum class ProfileType(val id: String, val label: String, val description: String) {
    DEFAULT("default", "Default", "Everyday use — balanced bubble and a full radial menu."),
    WORK("work", "Work", "Tighter bubble, productivity actions and the Work macro up front."),
    GAMING("gaming", "Gaming", "Small, low-opacity bubble tucked to the edge to stay out of the way."),
    READING("reading", "Reading", "Dim, minimal bubble with page-turn and note shortcuts."),
    MEDIA("media", "Media", "Volume, playback and capture controls front and centre."),
    CUSTOM("custom", "Custom", "A blank slate you shape entirely yourself."),
    ;

    val isBuiltIn: Boolean get() = this != CUSTOM

    companion object {
        fun fromId(id: String?): ProfileType =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * A profile is a bundle of independent configuration: bubble style, menu,
 * gestures, shortcuts, macros, theme and behaviour.
 */
data class Profile(
    val id: String = ProfileType.DEFAULT.id,
    val type: ProfileType = ProfileType.DEFAULT,
    val name: String = ProfileType.DEFAULT.label,
    val description: String = ProfileType.DEFAULT.description,
    val themeId: String = ThemePreset.MIDNIGHT_OBSIDIAN.id,
    val bubbleConfigId: String = "bubble-default",
    val radialConfigId: String = "radial-default",
    val isActive: Boolean = false,
    val autoSwitchPackage: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val sortOrder: Int = 0,
)
