package com.quantumff.segue.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One side of a [SwipeActionBox]: the [icon] a swipe reveals, in [tint], and its [label] for
 * accessibility. With [dismisses] the row slides out when it triggers, for an action that removes
 * the row; otherwise it springs back.
 */
@Immutable
class SwipeAction(val icon: ImageVector, val tint: Color, val label: String, val dismisses: Boolean = false)

/**
 * Symfonium's row swipes. Swiping right past [SWIPE_THRESHOLD] of the width runs [onStart]; swiping
 * left runs [onEnd]. A haptic ticks at the threshold. A null action leaves that direction alone.
 * Offsets are read in the draw phase only, so dragging does not recompose. Accessibility services
 * get both actions as custom actions.
 */
@Composable
fun SwipeActionBox(
    start: SwipeAction?,
    onStart: () -> Unit,
    end: SwipeAction?,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var widthPx by remember { mutableFloatStateOf(0f) }
    var pastThreshold by remember { mutableStateOf(false) }
    var comeBack by remember { mutableStateOf<Job?>(null) }
    val latestOnStart by rememberUpdatedState(onStart)
    val latestOnEnd by rememberUpdatedState(onEnd)

    val dragState = rememberDraggableState { delta ->
        // A side without an action does not move.
        val min = if (end == null) 0f else -widthPx
        val max = if (start == null) 0f else widthPx
        val target = (offset.value + delta).coerceIn(min, max)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(target) }
        val past = widthPx > 0f && abs(target) / widthPx >= SWIPE_THRESHOLD
        if (past && !pastThreshold) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        pastThreshold = past
    }
    Box(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .clipToBounds()
            .semantics {
                if (enabled) {
                    customActions = listOfNotNull(
                        start?.let { CustomAccessibilityAction(it.label) { latestOnStart().let { true } } },
                        end?.let { CustomAccessibilityAction(it.label) { latestOnEnd().let { true } } },
                    )
                }
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                // A new swipe owns the row; a dismissed row's late return must not undo it.
                onDragStarted = { comeBack?.cancel() },
                onDragStopped = {
                    pastThreshold = false
                    val fraction = if (widthPx > 0f) offset.value / widthPx else 0f
                    val action = when {
                        fraction >= SWIPE_THRESHOLD -> start
                        fraction <= -SWIPE_THRESHOLD -> end
                        else -> null
                    }
                    val onAction = if (fraction > 0f) latestOnStart else latestOnEnd
                    scope.launch {
                        when {
                            action == null -> offset.animateTo(0f, SegueMotion.player())

                            action.dismisses -> {
                                offset.animateTo(if (fraction > 0f) widthPx else -widthPx, SegueMotion.medium())
                                onAction()
                                // The row normally leaves the list now; come back if it did not.
                                comeBack = scope.launch {
                                    delay(DISMISS_CONFIRM_TIMEOUT_MILLIS)
                                    offset.animateTo(0f, SegueMotion.player())
                                }
                            }

                            else -> {
                                onAction()
                                offset.animateTo(0f, SegueMotion.player())
                            }
                        }
                    }
                },
            ),
    ) {
        SwipeBackground(start, end, offset = { offset.value }, modifier = Modifier.matchParentSize())
        // Rows are transparent at rest; while swiped the row is opaque over the revealed actions.
        val rowFill = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .drawBehind { if (offset.value != 0f) drawRect(rowFill) },
        ) { content() }
    }
}

/**
 * Track row swipes outside the queue (DESIGN.md §5.2): right = Play next (`list-start`), left = Add
 * to queue (`list-end`), both `primary`. Both spring back: the row stays where it is.
 */
@Composable
fun TrackSwipeBox(
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val playNextLabel = stringResource(R.string.ds_action_play_next)
    val addToQueueLabel = stringResource(R.string.ds_action_add_to_queue)
    SwipeActionBox(
        start = remember(primary, playNextLabel) { SwipeAction(SegueIcons.PlayNext, primary, playNextLabel) },
        onStart = onPlayNext,
        end = remember(primary, addToQueueLabel) { SwipeAction(SegueIcons.AddToQueue, primary, addToQueueLabel) },
        onEnd = onAddToQueue,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
private fun SwipeBackground(
    start: SwipeAction?,
    end: SwipeAction?,
    offset: () -> Float,
    modifier: Modifier = Modifier,
) {
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = modifier
            // Drawn only while swiping: at rest the transparent rows would show it.
            .drawBehind { if (offset() != 0f) drawRect(fill) }
            .padding(horizontal = SegueSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (start != null) {
            Icon(
                imageVector = start.icon,
                contentDescription = null,
                tint = start.tint,
                modifier = Modifier.graphicsLayer { alpha = if (offset() > 0f) 1f else 0f },
            )
        }
        Spacer(Modifier.weight(1f))
        if (end != null) {
            Icon(
                imageVector = end.icon,
                contentDescription = null,
                tint = end.tint,
                modifier = Modifier.graphicsLayer { alpha = if (offset() < 0f) 1f else 0f },
            )
        }
    }
}

/** How far across the row a swipe goes to trigger (DESIGN.md §4.8). */
private const val SWIPE_THRESHOLD = 0.4f
private const val DISMISS_CONFIRM_TIMEOUT_MILLIS = 1_500L
