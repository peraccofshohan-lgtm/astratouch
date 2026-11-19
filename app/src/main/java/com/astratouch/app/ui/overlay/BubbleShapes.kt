package com.astratouch.app.ui.overlay

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.astratouch.app.domain.model.BubbleShape
import kotlin.math.cos
import kotlin.math.sin

/** Maps a user-selected [BubbleShape] to a Compose [Shape]. */
fun bubbleShapeOf(shape: BubbleShape, sizeDp: Int): Shape = when (shape) {
    BubbleShape.CIRCLE -> CircleShape
    BubbleShape.SQUIRCLE -> RoundedCornerShape((sizeDp * 0.32f).dp)
    BubbleShape.ROUNDED_SQUARE -> RoundedCornerShape((sizeDp * 0.22f).dp)
    BubbleShape.HEXAGON -> PolygonShape(6, rotationDeg = 0f)
    BubbleShape.DIAMOND -> PolygonShape(4, rotationDeg = 0f)
    BubbleShape.SHIELD -> RoundedCornerShape(
        topStart = (sizeDp * 0.44f).dp,
        topEnd = (sizeDp * 0.44f).dp,
        bottomStart = (sizeDp * 0.12f).dp,
        bottomEnd = (sizeDp * 0.12f).dp,
    )
    BubbleShape.STAR -> StarShape(points = 5)
}

/** Regular polygon shape, drawn as a path so it stays crisp at any size. */
class PolygonShape(private val sides: Int, private val rotationDeg: Float = 0f) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy)
        val start = Math.toRadians((rotationDeg - 90f).toDouble())
        for (i in 0 until sides) {
            val angle = start + i * 2 * Math.PI / sides
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/** Five-point star, used for the "Astra" bubble design. */
class StarShape(private val points: Int = 5, private val innerRatio: Float = 0.45f) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = minOf(cx, cy)
        val inner = outer * innerRatio
        val step = Math.PI / points
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val angle = -Math.PI / 2 + i * step
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}
