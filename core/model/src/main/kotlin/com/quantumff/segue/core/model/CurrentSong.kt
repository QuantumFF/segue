package com.quantumff.segue.core.model

/**
 * The song the player is on and whether it plays: what song lists mark (DESIGN.md §5.2). Rows are
 * matched by song ID, so a song listed twice is marked twice.
 */
data class CurrentSong(val songId: String, val isPlaying: Boolean) {
    /** Whether a row of [song] is the current song's. */
    fun isOf(song: Song): Boolean = song.id == songId
}

/** The current song, or null when the queue is empty. */
val PlayerUiState.currentSong: CurrentSong?
    get() = currentItem?.let { CurrentSong(it.songId, isPlaying) }
