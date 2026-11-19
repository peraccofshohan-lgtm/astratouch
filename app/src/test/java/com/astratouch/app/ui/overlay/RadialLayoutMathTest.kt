package com.astratouch.app.ui.overlay

import com.astratouch.app.domain.model.RadialLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class RadialLayoutMathTest {

    @Test
    fun `every layout returns one position per slot`() {
        RadialLayout.entries.forEach { layout ->
            val positions = RadialLayoutMath.positions(layout, count = 6, radiusDp = 90, itemSizeDp = 48)
            assertEquals("layout $layout", 6, positions.size)
        }
    }

    @Test
    fun `empty menu yields no positions`() {
        assertTrue(RadialLayoutMath.positions(RadialLayout.FULL_CIRCLE, 0, 90, 48).isEmpty())
    }

    @Test
    fun `full circle keeps every item at the configured radius`() {
        val radius = 100f
        val positions = RadialLayoutMath.positions(RadialLayout.FULL_CIRCLE, 8, radius.toInt(), 48)
        positions.forEach { (x, y) ->
            assertEquals(radius, hypot(x, y), 0.5f)
        }
    }

    @Test
    fun `semi circle stays in the upper half`() {
        val positions = RadialLayoutMath.positions(RadialLayout.SEMI_CIRCLE, 5, 90, 48)
        // start angle 180° → angle π → cos = -1, sin = 0; sweeping to 360°
        positions.forEach { (_, y) -> assertTrue("y=$y", y <= 1f) }
    }

    @Test
    fun `quarter circle spans 90 degrees`() {
        val positions = RadialLayoutMath.positions(RadialLayout.QUARTER_CIRCLE, 4, 90, 48)
        val first = positions.first()
        val last = positions.last()
        assertTrue(first.first < 0f) // starts on the left
        assertTrue(last.first < 0.001f)
        assertTrue(last.second < 0f) // ends at the top
    }

    @Test
    fun `grid layout is symmetric and bounded`() {
        val positions = RadialLayoutMath.positions(RadialLayout.GRID, 9, 100, 48)
        assertEquals(9, positions.size)
        val maxX = positions.maxOf { kotlin.math.abs(it.first) }
        assertTrue("grid too wide: $maxX", maxX <= 140f)
    }

    @Test
    fun `compact is closer than expanded`() {
        val compact = RadialLayoutMath.positions(RadialLayout.COMPACT, 6, 100, 48)
        val expanded = RadialLayoutMath.positions(RadialLayout.EXPANDED, 6, 100, 48)
        val compactRadius = hypot(compact[0].first, compact[0].second)
        val expandedRadius = hypot(expanded[0].first, expanded[0].second)
        assertTrue(compactRadius < expandedRadius)
    }

    @Test
    fun `single item sits at the top of a circle`() {
        val positions = RadialLayoutMath.positions(RadialLayout.FULL_CIRCLE, 1, 80, 48)
        assertEquals(1, positions.size)
        assertEquals(0f, positions[0].first, 0.01f)
        assertEquals(-80f, positions[0].second, 0.01f)
    }

    @Test
    fun `angle helper matches the layout sweep`() {
        assertEquals(-90f, RadialLayoutMath.angleFor(RadialLayout.FULL_CIRCLE, 0, 4), 0.01f)
        assertEquals(0f, RadialLayoutMath.angleFor(RadialLayout.FULL_CIRCLE, 1, 4), 0.01f)
        assertEquals(180f, RadialLayoutMath.angleFor(RadialLayout.SEMI_CIRCLE, 0, 2), 0.01f)
        assertEquals(270f, RadialLayoutMath.angleFor(RadialLayout.QUARTER_CIRCLE, 1, 2), 0.01f)
    }
}
