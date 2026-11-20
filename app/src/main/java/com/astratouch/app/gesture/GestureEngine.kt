package com.astratouch.app.gesture

import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.GestureType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Pure, testable gesture recognition for the floating bubble.
 *
 * The engine is **edge-triggered**: it consumes pointer events pushed in by the
 * bubble composable and returns at most one recognised gesture per interaction.
 * It performs no screen polling and holds no Android dependencies, so the whole
 * recognition matrix is covered by fast JVM unit tests.
 *
 * Debouncing: after a gesture fires, [DEBOUNCE_MS] must elapse before another
 * fires, which prevents accidental double-triggering when a swipe ends exactly
 * where a tap began.
 *
 * Global interception limits (documented in docs/ACCESSIBILITY.md):
 * swipes that start inside the AstraTouch bubble are recognised locally. Android
 * does not allow an app to intercept touches that start in *other* apps without
 * an accessibility dialog overlay; AstraTouch does not do that, so system-wide
 * gestures remain owned by Android.
 */
class GestureEngine(
    private val mappingsProvider: () -> List<GestureMapping>,
    private val hapticsProvider: () -> Boolean = { true },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var pointerDownAt: Long = 0L
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var maxTravel: Float = 0f
    private var lastTapAt: Long = 0L
    private var lastTriggeredAt: Long = 0L
    private var longPressFired: Boolean = false
    private var active: Boolean = false

    /** Tracks an ongoing interaction without deciding yet. */
    fun onDown(x: Float, y: Float) {
        pointerDownAt = clock()
        downX = x
        downY = y
        maxTravel = 0f
        longPressFired = false
        active = true
    }

    /** Updates travel tracking; returns a LONG_PRESS trigger when it is due. */
    fun onMove(x: Float, y: Float, longPressTimeoutMs: Int = DEFAULT_LONG_PRESS_MS): GestureType? {
        if (!active) return null
        val travel = hypot(x - downX, y - downY)
        maxTravel = max(maxTravel, travel)
        val elapsed = clock() - pointerDownAt
        if (!longPressFired && travel < LONG_PRESS_SLOP * densityScale && elapsed >= longPressTimeoutMs) {
            longPressFired = true
            return submit(GestureType.LONG_PRESS)
        }
        return null
    }

    /**
     * Ends the interaction and returns the recognised gesture, if any.
     * A movement below the configured threshold is treated as a tap.
     */
    fun onUp(x: Float, y: Float, densityScale: Float = 1f): GestureType? {
        if (!active) return null
        active = false
        val elapsed = clock() - pointerDownAt
        val dx = x - downX
        val dy = y - downY
        val travel = hypot(dx, dy)

        if (longPressFired) return null

        val mapping = mappingFor(GestureType.SWIPE_UP)
        val threshold = (mapping?.effectiveThresholdPx() ?: DEFAULT_SWIPE_THRESHOLD_PX) * densityScale

        if (travel >= threshold) {
            val gesture = directionOf(dx, dy)
            return submit(gesture)
        }

        if (travel > TAP_SLOP * densityScale && elapsed > DEFAULT_LONG_PRESS_MS.toLong()) return null

        // Tap family: single vs double, resolved by time since the previous tap.
        val sinceLastTap = clock() - lastTapAt
        lastTapAt = clock()
        return if (sinceLastTap in 1..DOUBLE_TAP_WINDOW_MS) {
            submit(GestureType.DOUBLE_TAP)
        } else {
            submit(GestureType.SINGLE_TAP)
        }
    }

    fun onCancel() {
        active = false
        longPressFired = false
    }

    /** Which gesture should be mapped, or null when unbound/disabled. */
    fun mappingFor(type: GestureType): GestureMapping? =
        mappingsProvider().firstOrNull { it.gesture == type }

    fun isBound(type: GestureType): Boolean = mappingFor(type) != null

    fun hapticsEnabled(type: GestureType): Boolean =
        hapticsProvider() && (mappingFor(type)?.hapticsEnabled ?: false)

    private fun submit(type: GestureType): GestureType? {
        val now = clock()
        if (now - lastTriggeredAt < DEBOUNCE_MS) return null
        if (mappingFor(type) == null) return null
        lastTriggeredAt = now
        return type
    }

    /**
     * Classifies a swipe. Horizontal takes precedence when |dx| dominates,
     * which matches the visual expectation for diagonal drags.
     */
    fun directionOf(dx: Float, dy: Float): GestureType {
        val ax = abs(dx)
        val ay = abs(dy)
        return if (ax >= ay) {
            if (dx > 0) GestureType.SWIPE_RIGHT else GestureType.SWIPE_LEFT
        } else {
            if (dy > 0) GestureType.SWIPE_DOWN else GestureType.SWIPE_UP
        }
    }

    companion object {
        const val DOUBLE_TAP_WINDOW_MS = 280L
        const val DEFAULT_LONG_PRESS_MS = 420
        const val DEFAULT_SWIPE_THRESHOLD_PX = 48
        const val DEBOUNCE_MS = 120L
        const val TAP_SLOP = 14f
        const val LONG_PRESS_SLOP = 18f

        fun actionsFor(type: GestureType, mappings: List<GestureMapping>): String? =
            mappings.firstOrNull { it.gesture == type }?.actionId
    }

    private val densityScale: Float = 1f
}
