package com.astratouch.app.domain.model

import kotlinx.serialization.Serializable

/**
 * A single configuration value that a floating bubble can render.
 *
 * Persisted as columns in `bubble_configs` (see docs/DATABASE.md) so that the
 * overlay can react to individual field changes without re-reading the whole row.
 */
@Serializable
data class BubbleConfig(
    val id: String = "bubble-default",
    val profileId: String = ProfileType.DEFAULT.id,
    val sizeDp: Int = 56,
    val shape: BubbleShape = BubbleShape.CIRCLE,
    val iconKey: String = "ic_star",
    val opacity: Float = 0.92f,
    val colorStart: Long = 0xFF32E6E2,
    val colorEnd: Long = 0xFF7C5CFF,
    val borderWidthDp: Int = 1,
    val shadowRadiusDp: Int = 12,
    val blurRadiusDp: Int = 0,
    val iconTint: Long = 0xFFFFFFFF,
    val iconSizeDp: Int = 26,
    val edgeMarginDp: Int = 8,
    val snapMode: SnapMode = SnapMode.EDGE,
    val dockSide: DockSide = DockSide.RIGHT,
    val positionX: Int = -1,
    val positionY: Int = -1,
    val autoHideMs: Long = 0L,
    val autoDimAlpha: Float = 0.45f,
    val hapticsEnabled: Boolean = true,
    val animationStyle: AnimationStyle = AnimationStyle.SPRING,
    val animationSpeed: Float = 1.0f,
    val startupOnBoot: Boolean = false,
    val showEdgeHandle: Boolean = true,
    val edgePanelEnabled: Boolean = true,
) {
    val hasStoredPosition: Boolean get() = positionX >= 0 && positionY >= 0
}

@Serializable
enum class BubbleShape(val label: String) {
    CIRCLE("Circle"),
    SQUIRCLE("Squircle"),
    ROUNDED_SQUARE("Rounded square"),
    HEXAGON("Hexagon"),
    DIAMOND("Diamond"),
    SHIELD("Shield"),
    STAR("Star"),
    ;

    companion object {
        fun fromName(name: String?): BubbleShape =
            entries.firstOrNull { it.name == name } ?: CIRCLE
    }
}

@Serializable
enum class SnapMode(val label: String) {
    NONE("Free"),
    EDGE("Snap to edge"),
    GRID("Snap to grid"),
    ;

    companion object {
        fun fromName(name: String?): SnapMode = entries.firstOrNull { it.name == name } ?: EDGE
    }
}

@Serializable
enum class DockSide(val label: String) {
    LEFT("Left"),
    RIGHT("Right"),
    TOP("Top"),
    BOTTOM("Bottom"),
    ;

    val isHorizontal: Boolean get() = this == LEFT || this == RIGHT

    companion object {
        fun fromName(name: String?): DockSide = entries.firstOrNull { it.name == name } ?: RIGHT
    }
}

@Serializable
enum class AnimationStyle(val label: String) {
    NONE("None"),
    SPRING("Spring"),
    FADE("Fade"),
    SCALE("Scale"),
    SLIDE("Slide"),
    ;

    companion object {
        fun fromName(name: String?): AnimationStyle = entries.firstOrNull { it.name == name } ?: SPRING
    }
}
