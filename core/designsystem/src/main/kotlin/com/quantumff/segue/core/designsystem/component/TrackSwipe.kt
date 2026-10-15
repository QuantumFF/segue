package com.quantumff.segue.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a released track swipe settles. */
internal enum class TrackSwipe {
    Previous,
    Stay,
    Next,
}

/**
 * DESIGN.md §5.5: a swipe skips once it passes 30% of the width or is flung faster than
 * [flingPx] per second toward a neighbor. Negative offsets reveal the next track.
 */
internal fun settleTrackSwipe(
    offsetPx: Float,
    velocityPx: Float,
    widthPx: Float,
    flingPx: Float,
    hasPrevious: Boolean,
    hasNext: Boolean,
): TrackSwipe {
    if (widthPx <= 0f) return TrackSwipe.Stay
    val fraction = offsetPx / widthPx
    val towardNext = fraction <= -SWIPE_THRESHOLD || (offsetPx < 0f && velocityPx <= -flingPx)
    val towardPrevious = fraction >= SWIPE_THRESHOLD || (offsetPx > 0f && velocityPx >= flingPx)
    // A fling back toward the start cancels the swipe, however far it went.
    val flungBack = (offsetPx < 0f && velocityPx >= flingPx) || (offsetPx > 0f && velocityPx <= -flingPx)
    return when {
        flungBack -> TrackSwipe.Stay
        towardNext && hasNext -> TrackSwipe.Next
        towardPrevious && hasPrevious -> TrackSwipe.Previous
        else -> TrackSwipe.Stay
    }
}

/**
 * Pages between the current track and its neighbors like a pager (Symfonium's mini player). The
 * neighbors sit one width to each side and slide in with the drag; a committed swipe calls
 * [onSwipeToNext] or [onSwipeToPrevious] and stays on the neighbor until [currentKey] changes.
 * Offsets are read in the draw phase only, so dragging does not recompose.
 */
@Composable
internal fun TrackSwipeBox(
    current: MiniPlayerState,
    currentKey: Any?,
    previous: MiniPlayerState?,
    next: MiniPlayerState?,
    onSwipeToPrevious: () -> Unit,
    onSwipeToNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val flingPx = with(LocalDensity.current) { SWIPE_FLING_DP_PER_SECOND.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var pastThreshold by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // A committed skip waits at the neighbor for the new current track; no new drag until then.
    var awaitingSkip by remember { mutableStateOf(false) }
    val hasPrevious = previous != null
    val hasNext = next != null
    val latestOnPrevious by rememberUpdatedState(onSwipeToPrevious)
    val latestOnNext by rememberUpdatedState(onSwipeToNext)

    // The skipped-to track became current: it is already in place, so recentre without animating.
    LaunchedEffect(currentKey) {
        settleJob?.cancel()
        awaitingSkip = false
        offset.snapTo(0f)
    }

    val dragState = rememberDraggableState { delta ->
        val value = offset.value
        val blocked = (value + delta < 0f && !hasNext) || (value + delta > 0f && !hasPrevious)
        val target = value + if (blocked) delta * EDGE_RESISTANCE else delta
        // Undispatched, so the next delta in the same frame already sees this one.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(target) }
        val past = widthPx > 0f && abs(target) / widthPx >= SWIPE_THRESHOLD && !blocked
        if (past && !pastThreshold) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        pastThreshold = past
    }

    Box(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .clipToBounds()
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = !awaitingSkip,
                onDragStarted = { settleJob?.cancel() },
                onDragStopped = { velocity ->
                    pastThreshold = false
                    val outcome = settleTrackSwipe(offset.value, velocity, widthPx, flingPx, hasPrevious, hasNext)
                    settleJob = scope.launch {
                        when (outcome) {
                            TrackSwipe.Stay -> offset.animateTo(0f, SegueMotion.player(), velocity)

                            TrackSwipe.Next, TrackSwipe.Previous -> {
                                val toNext = outcome == TrackSwipe.Next
                                awaitingSkip = true
                                offset.animateTo(if (toNext) -widthPx else widthPx, SegueMotion.player(), velocity)
                                if (toNext) latestOnNext() else latestOnPrevious()
                                // Normally the new current track recentres first; this covers a skip that failed.
                                delay(SKIP_CONFIRM_TIMEOUT_MILLIS)
                                awaitingSkip = false
                                offset.animateTo(0f, SegueMotion.player())
                            }
                        }
                    }
                },
            ),
    ) {
        previous?.let { track ->
            MiniPlayerTrack(track, Modifier.graphicsLayer { translationX = offset.value - size.width })
        }
        MiniPlayerTrack(current, Modifier.graphicsLayer { translationX = offset.value })
        next?.let { track ->
            MiniPlayerTrack(track, Modifier.graphicsLayer { translationX = offset.value + size.width })
        }
    }
}

private const val SWIPE_THRESHOLD = 0.3f
private const val SWIPE_FLING_DP_PER_SECOND = 1000
private const val EDGE_RESISTANCE = 0.3f
private const val SKIP_CONFIRM_TIMEOUT_MILLIS = 1_500L
