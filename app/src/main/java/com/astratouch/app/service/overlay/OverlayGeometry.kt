package com.astratouch.app.service.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager
import com.astratouch.app.domain.model.DockSide
import com.astratouch.app.domain.model.SnapMode
import kotlin.math.roundToInt

/**
 * Pure geometry for the floating bubble: snapping, inset clamping and
 * rotation-safe repositioning. Kept free of Android view code so it is unit
 * tested directly.
 */
object OverlayGeometry {

    data class ScreenBounds(val width: Int, val height: Int, val insetTop: Int = 0, val insetBottom: Int = 0)

    fun screenBounds(context: Context): ScreenBounds {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(metrics)
        val width = metrics.widthPixels.takeIf { it > 0 } ?: 1080
        val height = metrics.heightPixels.takeIf { it > 0 } ?: 2340
        val orientation = context.resources.configuration.orientation
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        // Conservative insets so the bubble never hides under the status bar or
        // the gesture/navigation bar on either navigation mode.
        val top = if (isLandscape) 0 else (metrics.density * 32).roundToInt()
        val bottom = if (isLandscape) 0 else (metrics.density * 48).roundToInt()
        return ScreenBounds(width, height, top, bottom)
    }

    /**
     * Applies the configured snapping/clamping rules.
     *
     * The bubble is always clamped inside the usable area (screen minus status
     * bar, navigation bar and the configured margin). When [SnapMode.EDGE] is
     * active it docks to the nearest vertical edge, which is what makes "drag
     * to the left, stays on the left across rotations" work.
     *
     * @return the sanitised (x, y) top-left position of the bubble.
     */
    fun snapAndClamp(
        x: Int,
        y: Int,
        bubbleSizePx: Int,
        bounds: ScreenBounds,
        marginPx: Int,
        snapMode: SnapMode,
        preferredSide: DockSide?,
    ): Pair<Int, Int> {
        val minX = marginPx
        val maxX = (bounds.width - bubbleSizePx - marginPx).coerceAtLeast(minX)
        val minY = bounds.insetTop + marginPx
        val maxY = (bounds.height - bubbleSizePx - bounds.insetBottom - marginPx).coerceAtLeast(minY)

        var nx = x.coerceIn(minX, maxX)
        var ny = y.coerceIn(minY, maxY)

        when (snapMode) {
            SnapMode.EDGE -> {
                val dock = preferredSide ?: dockSideFor(nx, bubbleSizePx, bounds)
                when (dock) {
                    DockSide.LEFT -> nx = minX
                    DockSide.RIGHT -> nx = maxX
                    DockSide.TOP -> ny = minY
                    DockSide.BOTTOM -> ny = maxY
                }
            }

            SnapMode.GRID -> {
                val stepX = (bubbleSizePx * 0.5f).roundToInt().coerceAtLeast(8)
                val stepY = stepX
                nx = (nx.toFloat() / stepX).roundToInt().times(stepX).coerceIn(minX, maxX)
                ny = (ny.toFloat() / stepY).roundToInt().times(stepY).coerceIn(minY, maxY)
            }

            SnapMode.NONE -> Unit
        }

        return nx to ny
    }

    /** Chooses the dock side that best matches a free position. */
    fun dockSideFor(x: Int, bubbleSizePx: Int, bounds: ScreenBounds): DockSide =
        if (x + bubbleSizePx / 2 < bounds.width / 2) DockSide.LEFT else DockSide.RIGHT

    /** Repositions a stored position after a rotation or display-size change. */
    fun rescaleForBounds(
        x: Int,
        y: Int,
        previous: ScreenBounds,
        current: ScreenBounds,
        bubbleSizePx: Int,
        marginPx: Int,
    ): Pair<Int, Int> {
        if (previous.width <= 0 || previous.height <= 0) {
            return snapAndClamp(x, y, bubbleSizePx, current, marginPx, SnapMode.EDGE, null)
        }
        val ratioX = x.toFloat() / previous.width
        val ratioY = y.toFloat() / previous.height
        val nil = (ratioX * current.width).roundToInt()
        val niy = (ratioY * current.height).roundToInt()
        return snapAndClamp(nil, niy, bubbleSizePx, current, marginPx, SnapMode.EDGE, null)
    }

    /**
     * Menu panel placement: centres the panel on the bubble but keeps it fully
     * on screen, accounting for the radius of the radial layout.
     */
    fun menuCenter(
        bubbleX: Int,
        bubbleY: Int,
        bubbleSizePx: Int,
        menuSizePx: Int,
        bounds: ScreenBounds,
        marginPx: Int,
    ): Pair<Int, Int> {
        val desiredCx = bubbleX + bubbleSizePx / 2
        val desiredCy = bubbleY + bubbleSizePx / 2
        val half = menuSizePx / 2
        val cx = desiredCx.coerceIn(half + marginPx, (bounds.width - half - marginPx).coerceAtLeast(half + marginPx))
        val cy = desiredCy.coerceIn(
            bounds.insetTop + half + marginPx,
            (bounds.height - bounds.insetBottom - half - marginPx).coerceAtLeast(bounds.insetTop + half + marginPx),
        )
        return cx to cy
    }

    /** True when two touch rects would collide (used for edge-panel avoidance). */
    fun collides(a: Rect, b: Rect): Boolean = Rect.intersects(a, b)
}
