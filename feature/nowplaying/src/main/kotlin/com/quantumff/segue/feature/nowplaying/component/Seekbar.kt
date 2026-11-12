package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.quantumff.segue.core.designsystem.component.SeekLine
import com.quantumff.segue.core.designsystem.component.SeekLineStyle
import com.quantumff.segue.core.designsystem.component.overflowingTouchTarget
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.designsystem.theme.tabularNumbers
import com.quantumff.segue.core.designsystem.util.formatDuration
import com.quantumff.segue.feature.nowplaying.R

/**
 * Now Playing seekbar (DESIGN.md §4.7): a [SeekLine] with a 4 tall track (white 24%, active white)
 * and a 12 thumb that grows to 20 while dragging. Below it: elapsed time, the [quality] pill, and
 * the total (tap to switch to remaining). Tap or drag to seek; the line and the toggle are 48 tall
 * to touch while keeping their slots, and the line wins where the two overlap.
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
    // The drag position as a fraction, or null when not dragging: the times follow the drag.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val latestPosition by rememberUpdatedState(positionMillis)
    val active = SegueTheme.colors.onArt
    Column(modifier.fillMaxWidth()) {
        SeekLine(
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            isPlaying = isPlaying,
            onSeek = onSeek,
            style = SeekLineStyle.nowPlaying(active = active, inactive = active.copy(alpha = TRACK_ALPHA)),
            onDrag = { dragFraction = it },
            // Above the times, so the line's touch strip wins where the toggle's target reaches into it.
            modifier = Modifier.zIndex(1f),
        )
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
        // At least 48 wide, growing leftwards (the digits stay at the end), and 48 tall to touch
        // without growing the row, overlapping the seek line above (which wins) and the controls below.
        TimeText(
            millis = { if (showRemaining) positionMillis() - durationMillis else durationMillis },
            textAlign = TextAlign.End,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .widthIn(min = SegueSizes.touchTarget)
                .overflowingTouchTarget(
                    Modifier.clickable(onClickLabel = toggleLabel, role = Role.Button) {
                        showRemaining = !showRemaining
                    },
                ),
        )
    }
}

/**
 * Its own scope, so position ticks recompose only this text, and only when the shown second
 * changes (the ticker samples twice a second). Negative values read "−1:23".
 */
@Composable
private fun TimeText(millis: () -> Long, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val latestMillis by rememberUpdatedState(millis)
    val seconds by remember { derivedStateOf { latestMillis() / MILLIS_PER_SECOND } }
    val text = formatDuration(kotlin.math.abs(seconds))
    Text(
        text = if (seconds < 0) "−$text" else text,
        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
        color = SegueTheme.colors.onArtMuted,
        textAlign = textAlign,
        modifier = modifier.padding(vertical = SegueSpacing.xs),
    )
}

private val PILL_MAX_WIDTH = 160.dp
private const val TRACK_ALPHA = 0.24f
private const val PILL_BORDER_ALPHA = 0.4f
private const val MILLIS_PER_SECOND = 1_000L
