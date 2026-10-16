package com.quantumff.segue.core.model

/**
 * Play intents raised by browse screens. Features take these as callbacks and never talk to the
 * player themselves; the app routes them to playback. Create one per app and keep it, so screens
 * receive the same instance on every recomposition.
 */
class PlayActions(
    /**
     * Replaces the queue with [songs] and starts at the song at index `startIndex`. With `shuffle`,
     * `startIndex` is ignored and the songs are queued in a fresh shuffled order, from the first. A
     * running queue is never reshuffled.
     */
    val onPlaySongs: (songs: List<Song>, startIndex: Int, shuffle: Boolean) -> Unit,
    /** "Play albums" / "Shuffle albums": whole albums of a source, in order or shuffled, placed in the queue. */
    val onPlayAlbums: (request: AlbumPlayRequest) -> Unit,
    /** Inserts the songs right after the current track. */
    val onPlayNext: (songs: List<Song>) -> Unit,
    /** Appends the songs to the end of the queue. */
    val onAddToQueue: (songs: List<Song>) -> Unit,
    /** Starts the seed song, then songs similar to it (`getSimilarSongs2`). */
    val onStartInstantMix: (seed: Song) -> Unit,
) {
    /** "Shuffle albums" of [source], replacing the queue. */
    fun shuffleAlbums(source: AlbumSource) = onPlayAlbums(AlbumPlayRequest(source, AlbumOrder.Shuffled))

    companion object {
        /** Ignores every intent. For previews and tests. */
        val None = PlayActions(
            onPlaySongs = { _, _, _ -> },
            onPlayAlbums = {},
            onPlayNext = {},
            onAddToQueue = {},
            onStartInstantMix = {},
        )
    }
}
