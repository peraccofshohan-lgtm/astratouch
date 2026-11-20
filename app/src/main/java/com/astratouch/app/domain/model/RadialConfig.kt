package com.astratouch.app.domain.model

import kotlinx.serialization.Serializable

/** The eight required radial menu layouts (Part 6). */
@Serializable
enum class RadialLayout(val label: String) {
    FULL_CIRCLE("Full circle"),
    SEMI_CIRCLE("Semi circle"),
    QUARTER_CIRCLE("Quarter circle"),
    GRID("Grid"),
    COMPACT("Compact"),
    EXPANDED("Expanded"),
    NESTED("Nested"),
    MULTI_PAGE("Multi page"),
    ;

    /** Number of items rendered per page for this layout. */
    val capacity: Int
        get() = when (this) {
            FULL_CIRCLE -> 8
            SEMI_CIRCLE -> 6
            QUARTER_CIRCLE -> 4
            GRID -> 9
            COMPACT -> 5
            EXPANDED -> 12
            NESTED -> 8
            MULTI_PAGE -> 8
        }

    val supportsPaging: Boolean get() = this == MULTI_PAGE || this == GRID || this == EXPANDED

    companion object {
        fun fromName(name: String?): RadialLayout =
            entries.firstOrNull { it.name == name } ?: FULL_CIRCLE
    }
}

@Serializable
data class RadialConfig(
    val id: String = "radial-default",
    val profileId: String = ProfileType.DEFAULT.id,
    val layout: RadialLayout = RadialLayout.FULL_CIRCLE,
    val radiusDp: Int = 92,
    val spacingDp: Int = 8,
    val itemSizeDp: Int = 48,
    val backgroundStart: Long = 0xFF0E1018,
    val backgroundEnd: Long = 0xFF1B2030,
    val backgroundOpacity: Float = 0.92f,
    val blurRadiusDp: Int = 18,
    val accent: Long = 0xFF32E6E2,
    val animationStyle: AnimationStyle = AnimationStyle.SPRING,
    val animationSpeed: Float = 1.0f,
    val hapticsEnabled: Boolean = true,
    val showLabels: Boolean = true,
)

/** One placed command inside a radial page. */
@Serializable
data class RadialMenuItem(
    val id: String = "",
    val radialConfigId: String = "radial-default",
    val page: Int = 0,
    val slot: Int = 0,
    val actionId: String,
    val label: String = "",
    val iconKey: String = "",
    val category: String = "",
    val isFavorite: Boolean = false,
)

@Serializable
data class RecentAction(
    val actionId: String,
    val usedAt: Long,
)
