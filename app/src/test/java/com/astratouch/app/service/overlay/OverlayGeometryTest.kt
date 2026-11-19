package com.astratouch.app.service.overlay

import com.astratouch.app.domain.model.DockSide
import com.astratouch.app.domain.model.SnapMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGeometryTest {

    private val portrait = OverlayGeometry.ScreenBounds(width = 1080, height = 2340, insetTop = 80, insetBottom = 140)
    private val landscape = OverlayGeometry.ScreenBounds(width = 2340, height = 1080)
    private val size = 168
    private val margin = 24

    @Test
    fun `edge snap pushes to the nearest side`() {
        val (leftX, _) = OverlayGeometry.snapAndClamp(
            120, 900, size, portrait, margin, SnapMode.EDGE, DockSide.LEFT,
        )
        assertEquals(margin, leftX)

        val (rightX, _) = OverlayGeometry.snapAndClamp(
            120, 900, size, portrait, margin, SnapMode.EDGE, DockSide.RIGHT,
        )
        assertEquals(portrait.width - size - margin, rightX)
    }

    @Test
    fun `bubble can never leave the screen`() {
        val (x, y) = OverlayGeometry.snapAndClamp(
            -500, 99_999, size, portrait, margin, SnapMode.NONE, null,
        )
        assertTrue(x >= margin)
        assertTrue(y <= portrait.height - size - portrait.insetBottom - margin)
    }

    @Test
    fun `status bar and navigation bar insets are respected`() {
        val (_, top) = OverlayGeometry.snapAndClamp(
            100, -400, size, portrait, margin, SnapMode.NONE, null,
        )
        assertEquals(portrait.insetTop + margin, top)
    }

    @Test
    fun `grid snapping quantises position`() {
        val step = (size * 0.5f).toInt()
        val (x, y) = OverlayGeometry.snapAndClamp(
            333, 777, size, portrait, margin, SnapMode.GRID, null,
        )
        assertEquals(0, x % step)
        assertEquals(0, y % step)
    }

    @Test
    fun `dock side is derived from the horizontal centre`() {
        assertEquals(DockSide.LEFT, OverlayGeometry.dockSideFor(100, size, portrait))
        assertEquals(DockSide.RIGHT, OverlayGeometry.dockSideFor(900, size, portrait))
    }

    @Test
    fun `rotation rescales the stored position into the new bounds`() {
        val (x, y) = OverlayGeometry.rescaleForBounds(
            x = 900, y = 1200,
            previous = portrait, current = landscape,
            bubbleSizePx = size, marginPx = margin,
        )
        assertTrue(x in margin..(landscape.width - size - margin))
        assertTrue(y in margin..(landscape.height - size - margin))
    }

    @Test
    fun `rotation from unknown previous bounds falls back to a safe snap`() {
        val (x, y) = OverlayGeometry.rescaleForBounds(
            x = 40, y = 40, previous = OverlayGeometry.ScreenBounds(0, 0),
            current = portrait, bubbleSizePx = size, marginPx = margin,
        )
        assertTrue(x >= margin)
        assertTrue(y >= portrait.insetTop + margin)
    }

    @Test
    fun `menu centre stays fully on screen near a corner`() {
        val (cx, cy) = OverlayGeometry.menuCenter(
            bubbleX = 10, bubbleY = 10, bubbleSizePx = size,
            menuSizePx = 500, bounds = portrait, marginPx = margin,
        )
        assertTrue(cx >= 250 + margin - 1)
        assertTrue(cy >= portrait.insetTop + 250 + margin - 1)
    }

    @Test
    fun `collision detection reports overlapping rects`() {
        val a = OverlayGeometry.OverlayRect(0, 0, 10, 10)
        val b = OverlayGeometry.OverlayRect(5, 5, 15, 15)
        val c = OverlayGeometry.OverlayRect(20, 20, 30, 30)
        assertTrue(OverlayGeometry.collides(a, b))
        assertTrue(!OverlayGeometry.collides(a, c))
    }
}
