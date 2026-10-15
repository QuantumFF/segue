package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.designsystem.theme.tabularNumbers
import com.quantumff.segue.core.designsystem.util.formatDuration
import com.quantumff.segue.feature.nowplaying.R
import kotlinx.coroutines.flow.collectLatest

/**
 * Now Playing seekbar (DESIGN.md §4.7): a 4 tall track (white 24%, active white) and a 12 thumb
 * that grows to 20 while dragging. Below it: elapsed time, the [quality] pill, and the total (tap
 * to switch to remaining). Tap or drag to seek.
 *
 * Position samples arrive twice a second; between them the bar glides linearly to where the next
 * sample will be, so it moves smoothly while only this composable redraws.
 *
 * @param positionMillis read inside this composable only.
 */
@Composable
internal fun NowPlayingSeekbar(
    positionMillis: () -> Long,
    durationMillis: Long,
    isPlaying: Boolean,
    quality: String?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The drag position as a fraction, or null when not dragging.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val progress = remember { Animatable(0f) }
    val latestPosition by rememberUpdatedState(positionMillis)
    LaunchedEffect(durationMillis, isPlaying) {
        snapshotFlow { latestPosition() }.collectLatest { position ->
            if (durationMillis <= 0) {
                progress.snapTo(0f)
                return@collectLatest
            }
            val now = (position.toFloat() / durationMillis).coerceIn(0f, 1f)
            val jumped = kotlin.math.abs(now - progress.value) * durationMillis > JUMP_MILLIS
            if (!isPlaying || jumped) progress.snapTo(now) else progress.snapTo(maxOf(progress.value, now))
            if (isPlaying) {
                val ahead = ((position + TICK_MILLIS).toFloat() / durationMillis).coerceIn(0f, 1f)
                progress.animateTo(ahead, tween(TICK_MILLIS.toInt(), easing = LinearEasing))
            }
        }
    }
    val thumbRadius by animateDpAsState(
        targetValue = if (dragFraction != null) THUMB_DRAGGING / 2 else THUMB / 2,
        animationSpec = SegueMotion.short(),
        label = "thumb",
    )
    val latestDuration by rememberUpdatedState(durationMillis)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val active = SegueTheme.colors.onArt
    val inactive = active.copy(alpha = TRACK_ALPHA)
    val seekDescription = stringResource(R.string.feature_nowplaying_seek)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOUCH_HEIGHT)
                .semantics {
                    contentDescription = seekDescription
                    stateDescription = formatDuration(latestPosition() / MILLIS_PER_SECOND)
                    progressBarRangeInfo = ProgressBarRangeInfo(progress.value, 0f..1f)
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
                        onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let { latestOnSeek((it * latestDuration).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    )
                },
        ) {
            val fraction = dragFraction ?: progress.value
            val trackHeight = TRACK_HEIGHT.toPx()
            val top = (size.height - trackHeight) / 2
            val corner = CornerRadius(trackHeight / 2)
            drawRoundRect(inactive, Offset(0f, top), Size(size.width, trackHeight), corner)
            drawRoundRect(active, Offset(0f, top), Size(size.width * fraction, trackHeight), corner)
            drawCircle(active, thumbRadius.toPx(), Offset(size.width * fraction, size.height / 2))
        }
        SeekbarTimes(
            positionMillis = { dragFraction?.let { (it * durationMillis).toLong() } ?: latestPosition() },
            durationMillis = durationMillis,
            quality = quality,
        )
    }
}

@Composable
private fun SeekbarTimes(positionMillis: () -> Long, durationMillis: Long, quality: String?) {
    var showRemaining by rememberSaveable { mutableStateOf(false) }
    val muted = SegueTheme.colors.onArtMuted
    Box(Modifier.fillMaxWidth()) {
        TimeText(
            millis = { positionMillis() },
            modifier = Modifier.align(Alignment.CenterStart),
        )
        quality?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = muted,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = PILL_MAX_WIDTH)
                    .border(SegueSizes.hairline, muted.copy(alpha = PILL_BORDER_ALPHA), SegueShapes.full)
                    .padding(horizontal = SegueSpacing.sm, vertical = SegueSpacing.xxs),
            )
        }
        val toggleLabel = stringResource(
            if (showRemaining) R.string.feature_nowplaying_show_total else R.string.feature_nowplaying_show_remaining,
        )
        TimeText(
            millis = { if (showRemaining) positionMillis() - durationMillis else durationMillis },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable(onClickLabel = toggleLabel) { showRemaining = !showRemaining },
        )
    }
}

/** Its own scope, so position ticks recompose only this text. Negative values read "−1:23". */
@Composable
private fun TimeText(millis: () -> Long, modifier: Modifier = Modifier) {
    val value = millis()
    val text = formatDuration(kotlin.math.abs(value) / MILLIS_PER_SECOND)
    Text(
        text = if (value < 0) "−$text" else text,
        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
        color = SegueTheme.colors.onArtMuted,
        modifier = modifier.padding(vertical = SegueSpacing.xs),
    )
}

private val TRACK_HEIGHT = 4.dp
private val THUMB = 12.dp
private val THUMB_DRAGGING = 20.dp
private val TOUCH_HEIGHT = 32.dp
private val PILL_MAX_WIDTH = 160.dp
private const val TRACK_ALPHA = 0.24f
private const val PILL_BORDER_ALPHA = 0.4f
private const val MILLIS_PER_SECOND = 1_000L

/** The position ticker's interval in core:playback. */
private const val TICK_MILLIS = 500L

/** A sample further than this from the drawn position is a seek: snap instead of gliding. */
private const val JUMP_MILLIS = 1_500L
