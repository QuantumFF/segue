package com.quantumff.segue.core.model

/**
 * Play intents raised by browse screens. Features take these as callbacks and never talk to the
 * player themselves; the app routes them to playback. Create one per app and keep it, so screens
 * receive the same instance on every recomposition.
 */
class PlayActions(
    /** Replaces the queue with [songs] and starts at the song at index `startIndex`; `shuffle` shuffles tracks. */
    val onPlaySongs: (songs: List<Song>, startIndex: Int, shuffle: Boolean) -> Unit,
    val onShuffleAlbums: (source: AlbumShuffleSource) -> Unit,
    /** Inserts the songs right after the current track. */
    val onPlayNext: (songs: List<Song>) -> Unit,
    /** Appends the songs to the end of the queue. */
    val onAddToQueue: (songs: List<Song>) -> Unit,
    /** Starts a queue of songs similar to the song (`getSimilarSongs2`). */
    val onStartInstantMix: (songId: String) -> Unit,
) {
    companion object {
        /** Ignores every intent. For previews and tests. */
        val None = PlayActions(
            onPlaySongs = { _, _, _ -> },
            onShuffleAlbums = {},
            onPlayNext = {},
            onAddToQueue = {},
            onStartInstantMix = {},
        )
    }
}
