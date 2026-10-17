package com.quantumff.segue.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.theme.LocalReduceMotion
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Pinch to step a grid's card size (DESIGN.md §4.2). Spreading two fingers past [PINCH_STEP] calls
 * [onGrow]; pinching them together as far calls [onShrink]. Each returns whether the size changed,
 * and a change ticks a haptic. One pinch can step more than once.
 *
 * While pinching, the content follows the fingers a little (scale, clamped, read in the draw phase)
 * and springs back when the size steps or the fingers lift. Two-finger events are consumed so the
 * list under it does not scroll; one finger passes through. Accessibility services get the two
 * steps as custom actions.
 */
@Composable
fun Modifier.pinchToResize(onGrow: () -> Boolean, onShrink: () -> Boolean): Modifier {
    val haptics = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val latestOnGrow by rememberUpdatedState(onGrow)
    val latestOnShrink by rememberUpdatedState(onShrink)
    val scale = remember { Animatable(1f) }
    val origin = remember { floatArrayOf(HALF, HALF) }
    val scope = rememberCoroutineScope()
    val growLabel = stringResource(R.string.ds_grid_larger)
    val shrinkLabel = stringResource(R.string.ds_grid_smaller)
    return this
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(growLabel) { latestOnGrow() },
                CustomAccessibilityAction(shrinkLabel) { latestOnShrink() },
            )
        }
        .pointerInput(reduceMotion) {
            awaitEachGesture {
                var zoom = 1f
                var pinching = false
                do {
                    // The initial pass, so two fingers never reach the list's scroll.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        pinching = true
                        zoom *= event.calculateZoom()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (centroid.isSpecified && size.width > 0 && size.height > 0) {
                            origin[0] = centroid.x / size.width
                            origin[1] = centroid.y / size.height
                        }
                        val stepped = when {
                            zoom >= PINCH_STEP -> latestOnGrow()
                            zoom <= 1f / PINCH_STEP -> latestOnShrink()
                            else -> null
                        }
                        when (stepped) {
                            true -> {
                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                zoom = 1f
                            }

                            // At the smallest or largest size: hold at the edge, so turning back responds at once.
                            false -> zoom = zoom.coerceIn(1f / PINCH_STEP, PINCH_STEP)

                            null -> Unit
                        }
                        if (!reduceMotion) {
                            val follow = zoom.coerceIn(MIN_FOLLOW_SCALE, MAX_FOLLOW_SCALE)
                            scope.launch(start = CoroutineStart.UNDISPATCHED) { scale.snapTo(follow) }
                        }
                    }
                    // Once pinching, the finger left behind does not scroll either.
                    if (pinching) event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                if (pinching && !reduceMotion) scope.launch { scale.animateTo(1f, SegueMotion.player()) }
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            transformOrigin = TransformOrigin(origin[0], origin[1])
        }
}

/** How far the fingers spread (or close, as its inverse) to step one size. */
private const val PINCH_STEP = 1.3f

/** The content follows the pinch only this far, so it hints at the step without distorting the grid. */
private const val MIN_FOLLOW_SCALE = 0.94f
private const val MAX_FOLLOW_SCALE = 1.06f
private const val HALF = 0.5f
