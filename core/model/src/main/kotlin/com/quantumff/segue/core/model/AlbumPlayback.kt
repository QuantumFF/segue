package com.quantumff.segue.core.model

/**
 * A set of albums to play: "Play albums" and "Shuffle albums" queue them whole, and "Shuffle all"
 * queues their songs in a tracks shuffle.
 */
sealed interface AlbumSource {
    /**
     * Every album in the library, in no particular order: Library's "Shuffle all", Android Auto's
     * "Shuffle albums", and a voice "play some music" with an empty queue.
     */
    data object Library : AlbumSource

    /** The albums the Library Albums view shows for [sort] and [filter], in that order. */
    data class Albums(val sort: AlbumSortOrder, val filter: AlbumFilter = AlbumFilter()) : AlbumSource

    data class Artist(val artistId: String) : AlbumSource

    /**
     * The albums tagged with the genre, by name: what the genre's page lists. Every entry point
     * (the Genres tab, the genre page, an album's "By genre") means this same set.
     */
    data class Genre(val name: String) : AlbumSource

    /** The playlist's tracks grouped by album; only tracks on the playlist are queued. */
    data class Playlist(val playlistId: String) : AlbumSource
}

/**
 * The order a source's songs are queued in. Except in [ShuffledTracks], each album is queued whole,
 * in disc and track order.
 */
enum class AlbumOrder {
    /** The source's own order: for [AlbumSource.Albums], the order on screen. */
    InOrder,

    /** A seeded shuffle of the source's albums, all queued at once (albums shuffle): "Shuffle albums". */
    Shuffled,

    /** A shuffle of every song of the source's albums, all queued at once (tracks shuffle): "Shuffle all". */
    ShuffledTracks,
}

/** Where new songs go in the queue. */
enum class QueuePlacement {
    /** Replace the queue and start playing. */
    Replace,

    /** Right after the current track. */
    Next,

    /** At the end of the queue. */
    End,
}

/** "Play albums", "Shuffle albums" or "Shuffle all" of [source], placed by [placement]. */
data class AlbumPlayRequest(
    val source: AlbumSource,
    val order: AlbumOrder,
    val placement: QueuePlacement = QueuePlacement.Replace,
)
