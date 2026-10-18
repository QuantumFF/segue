package com.quantumff.segue.feature.nowplaying

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Drag-down and predictive-back collapse, drag-up to open Queue, and drag-down to close an open panel. */
internal class DismissGestures(val modifier: Modifier, val predictiveBack: @Composable () -> Unit)

/** While [panelDrag] is set, a drag down moves the open panel instead of the whole player. */
@Composable
internal fun rememberDismissGestures(
    onDismiss: () -> Unit,
    onSwipeUp: () -> Unit,
    panelDrag: PanelDrag?,
): DismissGestures {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragOffset = remember { Animatable(0f) }
    val backProgress = remember { Animatable(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var rawDrag by remember { mutableFloatStateOf(0f) }
    val flingPx = with(density) { DISMISS_FLING_DP_PER_SECOND.dp.toPx() }
    val backShiftPx = with(density) { BACK_SHIFT.toPx() }
    val latestPanelDrag by rememberUpdatedState(panelDrag)
    val dragState = rememberDraggableState { delta ->
        rawDrag += delta
        latestPanelDrag?.let {
            it.dragBy(delta)
            return@rememberDraggableState
        }
        // Undispatched, so a snap never lands after the release animation started and cancels it.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f))
        }
    }
    val modifier = Modifier
        .onSizeChanged { height = it.height.toFloat() }
        .graphicsLayer {
            val back = backProgress.value
            translationY = dragOffset.value + back * backShiftPx
            scaleX = 1f - back * BACK_SCALE
            scaleY = 1f - back * BACK_SCALE
            if (back > 0f) {
                shape = SegueShapes.xl
                clip = true
            }
        }
        .draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStarted = { rawDrag = 0f },
            onDragStopped = { velocity ->
                val offset = dragOffset.value
                val openPanel = latestPanelDrag
                when {
                    openPanel != null -> openPanel.release(velocity)

                    offset > height * DISMISS_FRACTION || (offset > 0f && velocity > flingPx) -> onDismiss()

                    rawDrag < -height * SWIPE_UP_FRACTION || velocity < -flingPx -> {
                        onSwipeUp()
                        dragOffset.animateTo(0f, SegueMotion.player())
                    }

                    else -> dragOffset.animateTo(0f, SegueMotion.player(), velocity)
                }
            },
        )
    val predictiveBack: @Composable () -> Unit = {
        PredictiveBackHandler { progress ->
            try {
                progress.collect { backProgress.snapTo(it.progress) }
                onDismiss()
            } catch (e: CancellationException) {
                scope.launch { backProgress.animateTo(0f, SegueMotion.player()) }
                throw e
            }
        }
    }
    return DismissGestures(modifier, predictiveBack)
}

@Composable
internal fun rememberPanelDrag(onClose: () -> Unit): PanelDrag {
    val scope = rememberCoroutineScope()
    val flingPx = with(LocalDensity.current) { DISMISS_FLING_DP_PER_SECOND.dp.toPx() }
    val latestOnClose by rememberUpdatedState(onClose)
    return remember(scope, flingPx) { PanelDrag(scope, flingPx) { latestOnClose() } }
}

/**
 * Drags an open phone panel down to close it, from outside the panel's list or past the top of it
 * ([nestedScroll]). The panel follows the finger, then slides out or settles back.
 */
@Stable
internal class PanelDrag(
    private val scope: CoroutineScope,
    private val flingPx: Float,
    private val onClose: () -> Unit,
) {
    private val offset = Animatable(0f)

    /** How far the panel is pulled down, in px. */
    val value: Float get() = offset.value

    /** The panel's height: how far it slides to leave. */
    var height by mutableFloatStateOf(0f)

    /** From a release that closes the panel until it has slid out. The art then comes in from above. */
    var isClosing by mutableStateOf(false)
        private set

    /** Pulls the panel by [delta] px, never above its resting place. Returns the part used. */
    fun dragBy(delta: Float): Float {
        if (isClosing) return 0f
        val current = offset.value
        val target = (current + delta).coerceAtLeast(0f)
        // Undispatched, so the next delta in the same frame already sees this one.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(target) }
        return target - current
    }

    fun release(velocity: Float) {
        if (isClosing) return
        val pulled = offset.value
        scope.launch {
            if (pulled > 0f && (pulled > height * PANEL_CLOSE_FRACTION || velocity > flingPx)) {
                isClosing = true
                onClose()
                // Stays there until the panel has left: see onPanelGone.
                offset.animateTo(height, SegueMotion.longExit())
            } else {
                offset.animateTo(0f, SegueMotion.player(), velocity)
            }
        }
    }

    /** The panel left the screen: the next one opens in place. */
    fun onPanelGone() {
        isClosing = false
        scope.launch { offset.snapTo(0f) }
    }

    /** Takes the list's leftover downward scroll at its top, and gives a pulled panel back before the list scrolls. */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y < 0f && offset.value > 0f) {
                Offset(0f, dragBy(available.y))
            } else {
                Offset.Zero
            }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y > 0f) {
                Offset(0f, dragBy(available.y))
            } else {
                Offset.Zero
            }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset.value == 0f) return Velocity.Zero
            release(available.y)
            return available
        }
    }
}

private val BACK_SHIFT = 48.dp
private const val DISMISS_FRACTION = 0.25f
private const val SWIPE_UP_FRACTION = 0.15f
private const val PANEL_CLOSE_FRACTION = 0.25f
private const val DISMISS_FLING_DP_PER_SECOND = 1000
private const val BACK_SCALE = 0.1f
