package com.quantumff.segue.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.util.formatDuration
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest

/**
 * How a [SeekLine] looks: its track, its thumb at rest and while dragging, and its slot. It takes
 * touches over 48 ([overflowingTouchTarget]), centered on the line, whatever its slot.
 */
@Immutable
data class SeekLineStyle(
    val active: Color,
    val inactive: Color,
    val trackHeight: Dp,
    val thumb: Dp,
    val thumbDragging: Dp,
    /** The height the line takes in its layout. */
    val height: Dp,
) {
    companion object {
        /**
         * Now Playing (DESIGN.md §4.7): a 4 tall track and a 12 thumb that grows to 20 while dragging.
         * It takes 32 in the layout but touches over 48, overlapping the times below.
         */
        fun nowPlaying(active: Color, inactive: Color) = SeekLineStyle(
            active = active,
            inactive = inactive,
            trackHeight = 4.dp,
            thumb = 12.dp,
            thumbDragging = 20.dp,
            height = 32.dp,
        )

        /**
         * The player bar (DESIGN.md §5.5): the 2 tall progress line, no thumb until it is dragged. It
         * takes 24 in the layout but touches over 48, overlapping its neighbors (which win where they
         * overlap, being drawn above it).
         */
        fun thin(active: Color, inactive: Color) = SeekLineStyle(
            active = active,
            inactive = inactive,
            trackHeight = SegueSizes.progressLine,
            thumb = 0.dp,
            thumbDragging = 12.dp,
            height = 24.dp,
        )
    }
}

/**
 * A seek slider: tap or drag to seek; a drag seeks once, where it ends, so it never fights the
 * position ticker. Screen readers get "Seek", "elapsed of total" (e.g. "1:52 of 4:15"), and a `setProgress` action.
 *
 * Position samples arrive twice a second; between them the line glides linearly to where the next
 * sample will be, so it moves smoothly while only this composable redraws.
 *
 * @param positionMillis read inside this composable only.
 * @param onDrag the drag position as a fraction while dragging, null when the drag ends.
 */
@Composable
fun SeekLine(
    positionMillis: () -> Long,
    durationMillis: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    style: SeekLineStyle,
    modifier: Modifier = Modifier,
    onDrag: (Float?) -> Unit = {},
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val latestOnDrag by rememberUpdatedState(onDrag)
    fun drag(fraction: Float?) {
        dragFraction = fraction
        latestOnDrag(fraction)
    }
    val progress = remember { Animatable(0f) }
    val latestPosition by rememberUpdatedState(positionMillis)
    LaunchedEffect(durationMillis, isPlaying) {
        snapshotFlow { latestPosition() }.collectLatest { position ->
            if (durationMillis <= 0) {
                progress.snapTo(0f)
                return@collectLatest
            }
            val now = (position.toFloat() / durationMillis).coerceIn(0f, 1f)
            val jumped = abs(now - progress.value) * durationMillis > JUMP_MILLIS
            if (!isPlaying || jumped) progress.snapTo(now) else progress.snapTo(maxOf(progress.value, now))
            if (isPlaying) {
                val ahead = ((position + TICK_MILLIS).toFloat() / durationMillis).coerceIn(0f, 1f)
                progress.animateTo(ahead, tween(TICK_MILLIS.toInt(), easing = LinearEasing))
            }
        }
    }
    val thumbRadius by animateDpAsState(
        targetValue = if (dragFraction != null) style.thumbDragging / 2 else style.thumb / 2,
        animationSpec = SegueMotion.short(),
        label = "thumb",
    )
    val latestDuration by rememberUpdatedState(durationMillis)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val seekDescription = stringResource(R.string.ds_seek)
    val resources = LocalResources.current

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(style.height)
            .overflowingTouchTarget(
                Modifier
                    .semantics {
                        contentDescription = seekDescription
                        // From the position samples, not the per-frame glide, so semantics update per tick.
                        val position = latestPosition()
                        stateDescription = resources.getString(
                            R.string.ds_seek_position,
                            formatDuration(position / MILLIS_PER_SECOND),
                            formatDuration(latestDuration / MILLIS_PER_SECOND),
                        )
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            if (latestDuration > 0) (position.toFloat() / latestDuration).coerceIn(0f, 1f) else 0f,
                            0f..1f,
                        )
                        setProgress { fraction ->
                            latestOnSeek((fraction * latestDuration).toLong())
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            latestOnSeek((fraction * latestDuration).toLong())
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> drag((offset.x / size.width).coerceIn(0f, 1f)) },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                drag((change.position.x / size.width).coerceIn(0f, 1f))
                            },
                            onDragEnd = {
                                dragFraction?.let { latestOnSeek((it * latestDuration).toLong()) }
                                drag(null)
                            },
                            onDragCancel = { drag(null) },
                        )
                    },
            ),
    ) {
        val fraction = dragFraction ?: progress.value
        val trackHeight = style.trackHeight.toPx()
        val top = (size.height - trackHeight) / 2
        val corner = CornerRadius(trackHeight / 2)
        drawRoundRect(style.inactive, Offset(0f, top), Size(size.width, trackHeight), corner)
        drawRoundRect(style.active, Offset(0f, top), Size(size.width * fraction, trackHeight), corner)
        if (thumbRadius > 0.dp) {
            drawCircle(style.active, thumbRadius.toPx(), Offset(size.width * fraction, size.height / 2))
        }
    }
}

private const val MILLIS_PER_SECOND = 1_000L

/** The position ticker's interval in core:playback. */
private const val TICK_MILLIS = 500L

/** A sample further than this from the drawn position is a seek: snap instead of gliding. */
private const val JUMP_MILLIS = 1_500L
