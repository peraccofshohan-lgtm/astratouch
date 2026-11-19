package com.astratouch.app.ui.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.astratouch.app.domain.model.AnimationStyle
import com.astratouch.app.domain.model.BubbleConfig
import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.GestureType
import com.astratouch.app.domain.model.RadialConfig
import com.astratouch.app.domain.model.RadialMenuItem
import com.astratouch.app.gesture.GestureEngine
import com.astratouch.app.service.overlay.OverlayState

/**
 * Root of the floating window content: the draggable bubble plus the radial
 * menu that opens around it.
 *
 * Touch handling is deliberately split so the bubble never consumes more than
 * it must: taps and drags are handled locally, and the menu renders as a sibling
 * overlay above the bubble.
 */
@Composable
fun AstraOverlayHost(
    state: OverlayState,
    bubble: BubbleConfig,
    radial: RadialConfig,
    items: List<RadialMenuItem>,
    gestures: List<GestureMapping>,
    hapticsEnabled: Boolean,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onGesture: (GestureType) -> Unit,
    onActionTap: (String, String?) -> Unit,
    onCloseMenu: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val currentGestures by rememberUpdatedState(gestures)
    val engine = remember {
        GestureEngine(
            mappingsProvider = { currentGestures },
            hapticsProvider = { hapticsEnabled },
        )
    }
    val currentOnGesture by rememberUpdatedState(onGesture)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    var pressedScale by remember { mutableFloatStateOf(1f) }
    val targetScale = if (pressedScale < 1f) 0.9f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(
            durationMillis = when (bubble.animationStyle) {
                AnimationStyle.NONE -> 0
                AnimationStyle.FADE, AnimationStyle.SCALE -> (120 / bubble.animationSpeed).toInt()
                else -> (160 / bubble.animationSpeed).toInt()
            },
        ),
        label = "bubbleScale",
    )

    val bubbleAlpha = if (state.menuVisible) bubble.autoDimAlpha.coerceAtLeast(0.35f) else bubble.opacity

    Box(modifier = Modifier.wrapContentSize()) {
        Box(
            modifier = Modifier
                .size(bubble.sizeDp.dp)
                .alpha(bubbleAlpha)
                .scale(scale)
                .clip(bubbleShapeOf(bubble.shape, bubble.sizeDp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(bubble.colorStart.toInt()),
                            Color(bubble.colorEnd.toInt()),
                        ),
                    ),
                )
                .border(
                    BorderStroke(bubble.borderWidthDp.dp, Color.White.copy(alpha = 0.22f)),
                    bubbleShapeOf(bubble.shape, bubble.sizeDp),
                )
                .pointerInput(bubble.id, bubble.snapMode, gestures) {
                    detectDragGestures(
                        onDragStart = {
                            pressedScale = 0.9f
                            engine.onDown(it.x, it.y)
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            engine.onMove(change.position.x, change.position.y)?.let(currentOnGesture)
                            currentOnDrag(dragAmount.x, dragAmount.y)
                        },
                        onDragEnd = {
                            pressedScale = 1f
                            engine.onUp(0f, 0f, density)?.let(currentOnGesture)
                            currentOnDragEnd()
                        },
                        onDragCancel = {
                            pressedScale = 1f
                            engine.onCancel()
                            currentOnDragEnd()
                        },
                    )
                }
                .pointerInput(bubble.id, gestures) {
                    detectTapGestures(
                        onTap = {
                            engine.onDown(it.x, it.y)
                            engine.onUp(it.x, it.y, density)?.let { g ->
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnGesture(g)
                            }
                        },
                        onDoubleTap = {
                            engine.onDown(it.x, it.y)
                            engine.onUp(it.x, it.y, density)?.let { g ->
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnGesture(g)
                            }
                        },
                        onLongPress = {
                            engine.onDown(it.x, it.y)
                            engine.onMove(it.x, it.y, longPressTimeoutMs = 0)?.let { g ->
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnGesture(g)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = IconRegistry.get(bubble.iconKey),
                contentDescription = "AstraTouch bubble",
                tint = Color(bubble.iconTint.toInt()),
                modifier = Modifier.size(bubble.iconSizeDp.dp),
            )
        }

        if (state.menuVisible) {
            RadialMenuOverlay(
                bubbleSizeDp = bubble.sizeDp,
                config = radial,
                items = items,
                hapticsEnabled = hapticsEnabled,
                onActionTap = onActionTap,
                onDismiss = onCloseMenu,
            )
        }
    }
}

