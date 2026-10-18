package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.quantumff.segue.core.designsystem.R

/**
 * Pinch to zoom a grid's card size (DESIGN.md §4.2). Each two-finger event goes to [onPinch]: the
 * fingers' centroid, how far it moved and how far they spread since the last event (below 1 when
 * they close). It returns whether the grid's column count changed, which ticks a haptic.
 * [onPinchEnd] follows once the fingers lift.
 *
 * Two-finger events are consumed so the list under it does not scroll; one finger passes through.
 * Accessibility services get a step either way, [onGrow] and [onShrink], as custom actions; each
 * returns whether the size changed.
 */
@Composable
fun Modifier.pinchToResize(
    onPinch: (centroid: Offset, pan: Offset, zoom: Float) -> Boolean,
    onPinchEnd: () -> Unit,
    onGrow: () -> Boolean,
    onShrink: () -> Boolean,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val latestOnPinch by rememberUpdatedState(onPinch)
    val latestOnPinchEnd by rememberUpdatedState(onPinchEnd)
    val latestOnGrow by rememberUpdatedState(onGrow)
    val latestOnShrink by rememberUpdatedState(onShrink)
    val growLabel = stringResource(R.string.ds_grid_larger)
    val shrinkLabel = stringResource(R.string.ds_grid_smaller)
    return this
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(growLabel) { latestOnGrow() },
                CustomAccessibilityAction(shrinkLabel) { latestOnShrink() },
            )
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                var pinching = false
                // Ends the pinch even when the gesture is cancelled, so the grid never stays zoomed.
                try {
                    do {
                        // The initial pass, so two fingers never reach the list's scroll.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            pinching = true
                            val stepped = latestOnPinch(
                                event.calculateCentroid(useCurrent = true),
                                event.calculatePan(),
                                event.calculateZoom(),
                            )
                            if (stepped) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                        // Once pinching, the finger left behind does not scroll either.
                        if (pinching) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                } finally {
                    if (pinching) latestOnPinchEnd()
                }
            }
        }
}
