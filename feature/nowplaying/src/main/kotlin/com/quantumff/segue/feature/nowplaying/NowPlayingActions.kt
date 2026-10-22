package com.quantumff.segue.feature.nowplaying

import androidx.compose.runtime.Immutable
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.Song

/** Every command Now Playing raises, grouped so the screen takes one stable parameter. */
@Immutable
class NowPlayingActions(
    val onCollapse: () -> Unit,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    /** Skips to a queue item by timeline index (an art swipe). */
    val onSkipTo: (Int) -> Unit,
    val onSeek: (Long) -> Unit,
    val onCycleRepeat: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onStartSleepTimer: (SleepTimerChoice) -> Unit,
    val onCancelSleepTimer: () -> Unit,
    val onInstantMix: () -> Unit,
    val onAlbumClick: (String) -> Unit,
    val onArtistClick: (String) -> Unit,
    /** Opens the album, playlist, artist or genre the queue came from (the top bar's source name). */
    val onSourceClick: (QueueSource) -> Unit,
    val onAddToPlaylist: (List<String>) -> Unit,
    val onVisiblePanelsChange: (Set<NowPlayingPanel>) -> Unit,
    val onRetryLyrics: () -> Unit,
    val onRetryRelated: () -> Unit,
    val onPlayRelated: (Int) -> Unit,
    val onPlayNext: (Song) -> Unit,
    val onAddToQueue: (List<Song>) -> Unit,
    val onSaveQueueAsPlaylist: (String) -> Unit,
    val onClearQueue: () -> Unit,
) {
    companion object {
        /** Ignores every command. For previews and tests. */
        val None = NowPlayingActions(
            onCollapse = {},
            onPlayPause = {},
            onPrevious = {},
            onNext = {},
            onSkipTo = {},
            onSeek = {},
            onCycleRepeat = {},
            onToggleFavorite = {},
            onStartSleepTimer = {},
            onCancelSleepTimer = {},
            onInstantMix = {},
            onAlbumClick = {},
            onArtistClick = {},
            onSourceClick = {},
            onAddToPlaylist = {},
            onVisiblePanelsChange = {},
            onRetryLyrics = {},
            onRetryRelated = {},
            onPlayRelated = {},
            onPlayNext = {},
            onAddToQueue = {},
            onSaveQueueAsPlaylist = {},
            onClearQueue = {},
        )
    }
}
