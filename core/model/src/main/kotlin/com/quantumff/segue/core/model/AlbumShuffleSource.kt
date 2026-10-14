package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList

/**
 * What a "Shuffle albums" action shuffles (PLAN.md, "Album shuffle"): the source's albums are
 * queued as blocks in random order, each block in disc and track order.
 */
sealed interface AlbumShuffleSource {
    /** Every album in the library. */
    data object Library : AlbumShuffleSource

    /** The artist's albums, including ones that credit them only in OpenSubsonic `artists`. */
    data class Artist(val artistId: String) : AlbumShuffleSource

    data class Genre(val name: String) : AlbumShuffleSource

    /** The playlist's songs, grouped into blocks by album. */
    data class Playlist(val playlistId: String) : AlbumShuffleSource

    /** An explicit set of albums. */
    data class Albums(val albumIds: ImmutableList<String>) : AlbumShuffleSource
}
