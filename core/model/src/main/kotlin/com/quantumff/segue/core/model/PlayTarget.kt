package com.quantumff.segue.core.model

/**
 * What a play intent queues, apart from album sources ([AlbumPlayRequest]): songs the screen
 * already has, or an album, playlist or artist (or several) whose songs are looked up when the
 * intent runs.
 */
sealed interface PlayTarget {
    /** [songs] as given. Replacing the queue starts at [startIndex], unless shuffled. */
    data class Songs(val songs: List<Song>, val startIndex: Int = 0) : PlayTarget

    /** The album's songs in disc and track order. */
    data class Album(val albumId: String) : PlayTarget

    /** The playlist's songs in playlist order. */
    data class Playlist(val playlistId: String) : PlayTarget

    /**
     * The artist's top songs, or when the server has none (or cannot be reached) the songs of their
     * albums in page order ([ArtistAlbums.inPageOrder]).
     */
    data class Artist(val artistId: String) : PlayTarget

    /** The songs of each of [targets] in turn: a multi-selection of albums or artists. */
    data class Each(val targets: List<PlayTarget>) : PlayTarget
}
