package com.astratouch.app.gesture

import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureEngineTest {

    private var now = 1_000L
    private lateinit var mappings: MutableList<GestureMapping>
    private lateinit var engine: GestureEngine

    @Before
    fun setUp() {
        mappings = GestureType.entries.mapIndexed { index, type ->
            GestureMapping(id = "g$index", gesture = type, actionId = "action.${type.name}")
        }.toMutableList()
        now = 1_000L
        engine = GestureEngine(
            mappingsProvider = { mappings },
            hapticsProvider = { true },
            clock = { now },
        )
    }

    @Test
    fun `tap without movement is a single tap`() {
        engine.onDown(100f, 100f)
        now += 80
        assertEquals(GestureType.SINGLE_TAP, engine.onUp(100f, 100f))
    }

    @Test
    fun `two rapid taps produce single then double tap`() {
        engine.onDown(100f, 100f)
        now += 60
        assertEquals(GestureType.SINGLE_TAP, engine.onUp(102f, 101f))

        now += 100 // inside the double-tap window
        engine.onDown(100f, 100f)
        now += 60
        assertEquals(GestureType.DOUBLE_TAP, engine.onUp(100f, 100f))
    }

    @Test
    fun `long press is recognised while the finger is still down`() {
        engine.onDown(50f, 50f)
        assertNull(engine.onMove(50f, 50f, longPressTimeoutMs = GestureEngine.DEFAULT_LONG_PRESS_MS))
        now += GestureEngine.DEFAULT_LONG_PRESS_MS + 10
        assertEquals(GestureType.LONG_PRESS, engine.onMove(51f, 51f))
        // Releasing after a long press must not also emit a tap.
        assertNull(engine.onUp(51f, 51f))
    }

    @Test
    fun `swipe directions are classified correctly`() {
        assertEquals(GestureType.SWIPE_RIGHT, engine.directionOf(120f, 10f))
        assertEquals(GestureType.SWIPE_LEFT, engine.directionOf(-120f, -10f))
        assertEquals(GestureType.SWIPE_DOWN, engine.directionOf(8f, 200f))
        assertEquals(GestureType.SWIPE_UP, engine.directionOf(-8f, -200f))
        // Horizontal wins on a perfect diagonal.
        assertEquals(GestureType.SWIPE_RIGHT, engine.directionOf(100f, 100f))
    }

    @Test
    fun `swipe beyond threshold is recognised`() {
        engine.onDown(10f, 400f)
        now += 120
        val result = engine.onUp(10f, 100f, densityScale = 1f)
        assertEquals(GestureType.SWIPE_UP, result)
    }

    @Test
    fun `movement below threshold stays a tap`() {
        engine.onDown(10f, 400f)
        now += 60
        assertEquals(GestureType.SINGLE_TAP, engine.onUp(10f, 380f, densityScale = 1f))
    }

    @Test
    fun `sensitivity halves the required travel`() {
        val mapping = GestureMapping(
            gesture = GestureType.SWIPE_LEFT,
            actionId = "action",
            swipeThresholdPx = 100,
            sensitivity = 2f,
        )
        assertEquals(50, mapping.effectiveThresholdPx())

        val insensitive = mapping.copy(sensitivity = 0.5f)
        assertEquals(200, insensitive.effectiveThresholdPx())
    }

    @Test
    fun `sensitivity is clamped to a safe range`() {
        val extreme = GestureMapping(gesture = GestureType.SWIPE_LEFT, actionId = "a", swipeThresholdPx = 48, sensitivity = 99f)
        assertTrue(extreme.effectiveThresholdPx() >= 8)
    }

    @Test
    fun `debounce blocks a second immediate gesture`() {
        engine.onDown(10f, 10f)
        now += 10
        assertEquals(GestureType.SINGLE_TAP, engine.onUp(10f, 10f))

        // Immediately repeating a swipe should be swallowed by the debounce window.
        now += 5
        engine.onDown(10f, 300f)
        now += 5
        assertNull(engine.onUp(10f, 50f))
    }

    @Test
    fun `unbound gestures are not emitted`() {
        mappings.removeAll { it.gesture == GestureType.SWIPE_DOWN }
        engine.onDown(10f, 10f)
        now += 100
        assertNull(engine.onUp(10f, 400f, densityScale = 1f))
    }

    @Test
    fun `cancel discards the interaction`() {
        engine.onDown(10f, 10f)
        engine.onCancel()
        now += 10
        assertNull(engine.onUp(10f, 10f))
    }

    @Test
    fun `haptics follow the mapping and global preference`() {
        assertTrue(engine.hapticsEnabled(GestureType.SINGLE_TAP))
        mappings[0] = mappings[0].copy(hapticsEnabled = false)
        assertTrue(!engine.hapticsEnabled(GestureType.SINGLE_TAP))
    }
}
