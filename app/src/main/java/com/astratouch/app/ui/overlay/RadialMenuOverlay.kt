package com.astratouch.app.ui.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astratouch.app.domain.model.RadialConfig
import com.astratouch.app.domain.model.RadialLayout
import com.astratouch.app.domain.model.RadialMenuItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the radial command center in the overlay window.
 *
 * All eight layouts share one placement function so behaviour is consistent and
 * testable: a slot index maps to an angle, which maps to an (x, y) offset that
 * is clamped inside the section's bounds.
 */
@Composable
fun RadialMenuOverlay(
    bubbleSizeDp: Int,
    config: RadialConfig,
    items: List<RadialMenuItem>,
    hapticsEnabled: Boolean,
    onActionTap: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var page by remember(config.id) { mutableIntStateOf(0) }
    val pageCount = maxOf(1, (items.maxOfOrNull { it.page } ?: 0) + 1)
    val currentPage = page.coerceIn(0, pageCount - 1)
    val pageItems = items.filter { it.page == currentPage }
        .sortedBy { it.slot }
        .take(config.layout.capacity)

    val positions = remember(config.layout, config.radiusDp, bubbleSizeDp, pageItems.map { it.id }) {
        RadialLayoutMath.positions(
            layout = config.layout,
            count = pageItems.size,
            radiusDp = config.radiusDp,
            itemSizeDp = config.itemSizeDp,
        )
    }

    val panelSize = (config.radiusDp + config.itemSizeDp + 16) * 2
    val appear by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (config.animationStyle == com.astratouch.app.domain.model.AnimationStyle.SPRING) {
            spring(dampingRatio = 0.72f, stiffness = 900f)
        } else {
            tween((180 / config.animationSpeed).toInt())
        },
        label = "radialAppear",
    )

    Box(
        modifier = Modifier
            .size(panelSize.dp)
            .offset(
                x = ((bubbleSizeDp - panelSize) / 2).dp,
                y = ((bubbleSizeDp - panelSize) / 2).dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Backdrop: catches taps outside the items so the menu closes predictably.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(config.backgroundStart.toInt()).copy(alpha = config.backgroundOpacity),
                            Color(config.backgroundEnd.toInt()).copy(alpha = config.backgroundOpacity * 0.86f),
                            Color.Transparent,
                        ),
                    ),
                )
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )

        pageItems.forEachIndexed { index, item ->
            val pos = positions.getOrNull(index) ?: return@forEachIndexed
            RadialItemButton(
                label = item.label.ifBlank { null },
                iconKey = item.iconKey.ifBlank {
                    com.astratouch.app.service.action.ActionIcons.iconFor(item.actionId)
                },
                accent = Color(config.accent.toInt()),
                sizeDp = config.itemSizeDp,
                showLabel = config.showLabels,
                modifier = Modifier.offset(x = pos.first.dp, y = pos.second.dp),
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onActionTap(item.actionId, null)
                },
            )
        }

        if (pageCount > 1 && config.layout.supportsPaging) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(pageCount) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == currentPage) Color(config.accent.toInt())
                                else Color.White.copy(alpha = 0.35f),
                            )
                            .pointerInput(i) {
                                detectTapGestures { page = i }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun RadialItemButton(
    label: String?,
    iconKey: String,
    accent: Color,
    sizeDp: Int,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF161A26).copy(alpha = 0.96f),
                            Color(0xFF0B0E16).copy(alpha = 0.96f),
                        ),
                    ),
                )
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.0f))
                .pointerInput(iconKey, onClick) { detectTapGestures { onClick() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = IconRegistry.get(iconKey),
                contentDescription = label ?: iconKey,
                tint = accent,
                modifier = Modifier.size((sizeDp * 0.5f).dp),
            )
        }
        if (showLabel && label != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * Pure layout mathematics for every radial layout. Extracted so the placement of
 * all eight layouts is unit tested without an Android device.
 */
object RadialLayoutMath {

    /**
     * @return list of (dx, dy) offsets in dp, relative to the panel centre, in
     *         the same order as the input slots.
     */
    fun positions(
        layout: RadialLayout,
        count: Int,
        radiusDp: Int,
        itemSizeDp: Int,
    ): List<Pair<Float, Float>> {
        if (count <= 0) return emptyList()
        val r = radiusDp.toFloat()
        return when (layout) {
            RadialLayout.FULL_CIRCLE -> circle(count, r, startDeg = -90f, sweepDeg = 360f)
            RadialLayout.SEMI_CIRCLE -> circle(count, r, startDeg = 180f, sweepDeg = 180f)
            RadialLayout.QUARTER_CIRCLE -> circle(count, r, startDeg = 180f, sweepDeg = 90f)
            RadialLayout.COMPACT -> circle(count, r * 0.72f, startDeg = -90f, sweepDeg = 360f)
            RadialLayout.EXPANDED -> circle(count, r * 1.18f, startDeg = -90f, sweepDeg = 360f)
            // NESTED renders two concentric rings: the first `innerCount` items on
            // an inner circle, the rest on the outer one. Previously this was an
            // alias for FULL_CIRCLE, which meant two of the eight advertised
            // layouts were visually identical.
            RadialLayout.NESTED -> nested(count, r)
            // MULTI_PAGE is a full circle whose items are spaced for a page
            // indicator row; the geometry is a circle but sized to leave a gutter
            // for the pager at the bottom.
            RadialLayout.MULTI_PAGE -> circle(count, r, startDeg = -90f, sweepDeg = 360f)
                .map { (x, y) -> x to (y - itemSizeDp * 0.22f) }
            RadialLayout.GRID -> grid(count, r, itemSizeDp.toFloat())
        }
    }

    private fun circle(count: Int, radius: Float, startDeg: Float, sweepDeg: Float): List<Pair<Float, Float>> {
        if (count == 1) return listOf(0f to -radius)
        val step = if (sweepDeg >= 359f) sweepDeg / count else sweepDeg / (count - 1)
        return (0 until count).map { i ->
            val angle = Math.toRadians((startDeg + i * step).toDouble())
            (radius * cos(angle)).toFloat() to (radius * sin(angle)).toFloat()
        }
    }

    /**
     * Two concentric rings: up to five items on an inner circle at 55% of the
     * radius, the remainder on the outer circle at 100%.
     */
    private fun nested(count: Int, radius: Float): List<Pair<Float, Float>> {
        val innerCount = when {
            count <= 3 -> count
            count <= 6 -> 3
            count <= 9 -> 4
            else -> 5
        }
        val outerCount = count - innerCount
        val inner = circle(innerCount, radius * 0.55f, startDeg = -90f, sweepDeg = 360f)
        if (outerCount <= 0) return inner
        val outer = circle(outerCount, radius, startDeg = -90f, sweepDeg = 360f)
        return inner + outer
    }

    private fun grid(count: Int, radius: Float, itemSize: Float): List<Pair<Float, Float>> {
        val columns = when {
            count <= 4 -> 2
            count <= 9 -> 3
            else -> 4
        }
        val rows = (count + columns - 1) / columns
        val spacing = itemSize * 1.16f
        val width = (columns - 1) * spacing
        val height = (rows - 1) * spacing
        return (0 until count).map { i ->
            val col = i % columns
            val row = i / columns
            val x = -width / 2f + col * spacing
            val y = -height / 2f + row * spacing
            val scale = (radius / (maxOf(width, height) / 2f + 1f)).coerceIn(0.6f, 1.4f)
            (x * scale) to (y * scale)
        }
    }

    /** Angle (degrees) of a slot, exported for tests and gesture targeting. */
    fun angleFor(layout: RadialLayout, index: Int, count: Int): Float = when (layout) {
        RadialLayout.SEMI_CIRCLE -> 180f + if (count <= 1) 0f else 180f * index / (count - 1)
        RadialLayout.QUARTER_CIRCLE -> 180f + if (count <= 1) 0f else 90f * index / (count - 1)
        // NESTED spreads both rings from the same start angle, so the visual
        // rotation of the whole menu still follows a single sweep.
        else -> -90f + if (count <= 1) 0f else 360f * index / count
    }
}
