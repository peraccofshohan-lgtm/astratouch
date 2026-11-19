package com.astratouch.app.domain.model

/** Gestures recognised by the AstraTouch gesture engine (Part 9). */
enum class GestureType(val label: String, val isSwipe: Boolean) {
    SINGLE_TAP("Single tap", false),
    DOUBLE_TAP("Double tap", false),
    LONG_PRESS("Long press", false),
    SWIPE_UP("Swipe up", true),
    SWIPE_DOWN("Swipe down", true),
    SWIPE_LEFT("Swipe left", true),
    SWIPE_RIGHT("Swipe right", true),
    ;

    companion object {
        fun fromName(name: String?): GestureType? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Binds a gesture to an action inside one profile.
 *
 * @param swipeThresholdPx minimum travel distance that counts as a swipe
 * @param sensitivity 0.2f (deliberate) … 2.0f (twitchy); scales the swipe threshold
 */
data class GestureMapping(
    val id: String = "",
    val profileId: String = ProfileType.DEFAULT.id,
    val gesture: GestureType,
    val actionId: String,
    val swipeThresholdPx: Int = 48,
    val sensitivity: Float = 1.0f,
    val hapticsEnabled: Boolean = true,
) {
    /** Effective travel requirement after applying user sensitivity. */
    fun effectiveThresholdPx(): Int =
        (swipeThresholdPx / sensitivity.coerceIn(0.2f, 2.0f)).toInt().coerceAtLeast(8)
}

/** Default gesture bindings used when a profile is first created. */
object DefaultGestureMappings {
    fun forProfile(profileId: String): List<GestureMapping> = listOf(
        GestureMapping(profileId = profileId, gesture = GestureType.SINGLE_TAP, actionId = ActionIds.OPEN_RADIAL_MENU),
        GestureMapping(profileId = profileId, gesture = GestureType.DOUBLE_TAP, actionId = ActionIds.SCREENSHOT),
        GestureMapping(profileId = profileId, gesture = GestureType.LONG_PRESS, actionId = ActionIds.HOME),
        GestureMapping(profileId = profileId, gesture = GestureType.SWIPE_UP, actionId = ActionIds.RECENTS),
        GestureMapping(profileId = profileId, gesture = GestureType.SWIPE_DOWN, actionId = ActionIds.NOTIFICATIONS),
        GestureMapping(profileId = profileId, gesture = GestureType.SWIPE_LEFT, actionId = ActionIds.BACK),
        GestureMapping(profileId = profileId, gesture = GestureType.SWIPE_RIGHT, actionId = ActionIds.QUICK_SETTINGS),
    )
}
