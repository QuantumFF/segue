package com.quantumff.segue.core.model

/** A set of albums to play whole: "Play albums" and "Shuffle albums" draw from one of these. */
sealed interface AlbumSource {
    /** Every album in the library, in no particular order. */
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

/** The order albums are queued in. Inside an album, tracks always keep disc and track order. */
enum class AlbumOrder {
    /** The source's own order: for [AlbumSource.Albums], the order on screen. */
    InOrder,

    /** A seeded shuffle of the source's albums. */
    Shuffled,
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

/** "Play albums" or "Shuffle albums" of [source], placed by [placement]. */
data class AlbumPlayRequest(
    val source: AlbumSource,
    val order: AlbumOrder,
    val placement: QueuePlacement = QueuePlacement.Replace,
)

/**
 * A running album feed: an album play queued in windows. The first window is queued at once; more
 * albums are appended as playback nears the end of what is queued, so a library of thousands of
 * albums never has to be loaded up front. Persisted, so it survives process death.
 *
 * The feed only extends a queue it built: [queueFingerprint] identifies the queue (its song IDs in
 * order) as the feed last left it, and any other edit (a track added, removed or moved, the queue
 * replaced or cleared) ends the feed.
 */
data class AlbumFeed(
    val source: AlbumSource,
    val order: AlbumOrder,
    /** Seeds the album shuffle, so the same feed always yields the same order. */
    val seed: Long,
    /** How many albums of the source's order are used up (queued, or skipped as unplayable). */
    val cursor: Int,
    val queueFingerprint: Long,
)
