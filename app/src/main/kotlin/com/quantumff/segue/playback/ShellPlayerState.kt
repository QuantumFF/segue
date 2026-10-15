package com.quantumff.segue.playback

import androidx.compose.runtime.Immutable
import com.quantumff.segue.core.designsystem.component.MiniPlayerState
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueueItem
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.ShuffleMode

/**
 * What the shell's player surfaces (mini player, player bar) show. Excludes the position, so the
 * position ticker does not recompose the shell; see [progressOf].
 */
@Immutable
data class ShellPlayerState(
    /** Null when the queue is empty: the mini player hides. */
    val current: MiniPlayerState? = null,
    /** The track a swipe right reveals and skips to. */
    val previous: AdjacentTrack? = null,
    /** The track a swipe left reveals and skips to. */
    val next: AdjacentTrack? = null,
    val durationMillis: Long = 0,
    val shuffleMode: ShuffleMode = ShuffleMode.Off,
    val repeatMode: RepeatMode = RepeatMode.Off,
)

/** A neighbor of the current track in play order, and the timeline index to skip to it. */
@Immutable
data class AdjacentTrack(val track: MiniPlayerState, val timelineIndex: Int)

/** Commands from the shell's player surfaces. Create once and keep, so they stay stable. */
@Immutable
class ShellPlayerActions(
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    /** Skips to a queue item by timeline index (a mini player swipe). */
    val onSkipTo: (Int) -> Unit,
    val onCycleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
) {
    companion object {
        /** Ignores every command. For previews and tests. */
        val None = ShellPlayerActions({}, {}, {}, {}, {}, {})
    }
}

internal fun PlayerUiState.toShellPlayerState(): ShellPlayerState {
    val item = currentItem ?: return ShellPlayerState()
    return ShellPlayerState(
        current = item.toMiniPlayer(isPlaying),
        previous = neighbor(-1),
        next = neighbor(+1),
        durationMillis = durationMillis,
        shuffleMode = shuffleMode,
        repeatMode = repeatMode,
    )
}

/** Playback progress in [0, 1]; 0 while the duration is unknown. */
internal fun progressOf(positionMillis: Long, durationMillis: Long): Float =
    if (durationMillis <= 0) 0f else (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)

private fun PlayerUiState.neighbor(step: Int): AdjacentTrack? {
    if (queue.size < 2 || currentQueueIndex !in queue.indices) return null
    var index = currentQueueIndex + step
    if (index !in queue.indices) {
        if (repeatMode != RepeatMode.All) return null
        index = (index + queue.size) % queue.size
    }
    val item = queue[index]
    return AdjacentTrack(item.toMiniPlayer(isPlaying), item.timelineIndex)
}

private fun QueueItem.toMiniPlayer(isPlaying: Boolean) = MiniPlayerState(
    title = title,
    artist = artist.orEmpty(),
    coverArtId = coverArtId,
    isPlaying = isPlaying,
)
