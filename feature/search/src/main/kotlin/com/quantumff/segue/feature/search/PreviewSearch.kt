package com.quantumff.segue.feature.search

import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ExplicitStatus
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.model.Song
import kotlinx.collections.immutable.persistentListOf

/** Sample data for search previews. */
internal object PreviewSearch {
    val genres = persistentListOf(
        Genre("Alternative", songCount = 120, albumCount = 12),
        Genre("Electronic", songCount = 80, albumCount = 9),
        Genre("Jazz", songCount = 40, albumCount = 4),
        Genre("Rock", songCount = 200, albumCount = 21),
    )

    private val artist = Artist(
        id = "ar-1",
        name = "Radiohead",
        albumCount = 9,
        coverArtId = null,
        starredAt = null,
        userRating = null,
        musicBrainzId = null,
    )

    private val album = Album(
        id = "al-1",
        name = "In Rainbows",
        artist = "Radiohead",
        artistId = "ar-1",
        artists = persistentListOf(),
        coverArtId = null,
        songCount = 10,
        durationSeconds = 2_550,
        year = 2007,
        genre = "Alternative",
        createdAt = null,
        starredAt = null,
        userRating = null,
        playCount = null,
        playedAt = null,
        isCompilation = false,
        discTitles = persistentListOf(),
        musicBrainzId = null,
        explicitStatus = ExplicitStatus.None,
    )

    val result = SearchResult(
        artists = persistentListOf(artist, artist.copy(id = "ar-2", name = "Radio Dept.")),
        albums = persistentListOf(album, album.copy(id = "al-2", name = "OK Computer", year = 1997)),
        songs = persistentListOf(
            song(id = "s-1", title = "Nude", seconds = 255),
            song(id = "s-2", title = "Reckoner", seconds = 290),
        ),
        includesServerResults = false,
    )

    private fun song(id: String, title: String, seconds: Int) = Song(
        id = id,
        title = title,
        albumId = "al-1",
        album = "In Rainbows",
        artist = "Radiohead",
        artistId = "ar-1",
        durationSeconds = seconds,
    )
}
