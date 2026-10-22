package com.quantumff.segue.core.testing.play

import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueueItem
import kotlinx.collections.immutable.persistentListOf

/** A player whose one-song queue is [songId], playing or paused as [isPlaying] says. */
fun playerStateOf(songId: String, isPlaying: Boolean = true): PlayerUiState {
    val item = QueueItem(
        songId = songId,
        timelineIndex = 0,
        title = "Song $songId",
        artist = null,
        album = null,
        albumId = null,
        coverArtId = null,
        durationMillis = 1_000,
        isStarred = false,
    )
    return PlayerUiState(
        currentItem = item,
        currentQueueIndex = 0,
        isPlaying = isPlaying,
        queue = persistentListOf(item),
    )
}
