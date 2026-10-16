package com.quantumff.segue.core.testing.data

import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ExplicitStatus
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Server
import com.quantumff.segue.core.model.Song
import kotlinx.collections.immutable.persistentListOf

/** Builders for domain models with sensible defaults, so tests only spell out what they check. */
object TestData {
    val server =
        Server(id = "server-1", name = "demo.navidrome.org", baseUrl = "https://demo.navidrome.org", username = "demo")

    fun album(
        id: String,
        name: String = "Album $id",
        artistId: String? = "artist-1",
        artist: String? = "Artist 1",
        songCount: Int = 2,
        year: Int? = 2007,
        genre: String? = "Rock",
        starredAt: Long? = null,
    ) = Album(
        id = id,
        name = name,
        artist = artist,
        artistId = artistId,
        artists = persistentListOf(),
        coverArtId = "al-$id",
        songCount = songCount,
        durationSeconds = songCount * 200,
        year = year,
        genre = genre,
        createdAt = null,
        starredAt = starredAt,
        userRating = null,
        playCount = null,
        playedAt = null,
        isCompilation = false,
        discTitles = persistentListOf(),
        musicBrainzId = null,
        explicitStatus = ExplicitStatus.None,
    )

    fun song(
        id: String,
        title: String = "Song $id",
        albumId: String? = "album-1",
        artistId: String? = "artist-1",
        track: Int? = null,
        discNumber: Int? = null,
        starredAt: Long? = null,
    ) = Song(
        id = id,
        title = title,
        albumId = albumId,
        album = albumId?.let { "Album $it" },
        artist = "Artist 1",
        artistId = artistId,
        track = track,
        discNumber = discNumber,
        durationSeconds = 200,
        coverArtId = albumId?.let { "al-$it" },
        starredAt = starredAt,
    )

    fun artist(id: String, name: String = "Artist $id", albumCount: Int = 3, starredAt: Long? = null) = Artist(
        id = id,
        name = name,
        albumCount = albumCount,
        coverArtId = "ar-$id",
        starredAt = starredAt,
        userRating = null,
        musicBrainzId = null,
    )

    fun playlist(id: String, name: String = "Playlist $id", songCount: Int = 2, readonly: Boolean = false) = Playlist(
        id = id,
        name = name,
        comment = null,
        owner = "demo",
        isPublic = false,
        songCount = songCount,
        durationSeconds = songCount * 200,
        coverArtId = "pl-$id",
        createdAt = null,
        changedAt = null,
        readonly = readonly,
    )

    fun genre(name: String, albumCount: Int = 3) =
        Genre(name = name, songCount = albumCount * 10, albumCount = albumCount)
}
