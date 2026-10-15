package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class OfflineLibraryTest {
    private fun album(id: String) = Album(
        id = id,
        name = id,
        artist = null,
        artistId = null,
        artists = persistentListOf(),
        coverArtId = null,
        songCount = 1,
        durationSeconds = 0,
        year = null,
        genre = null,
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

    private fun artist(id: String) = Artist(id, id, 1, null, null, null, null)

    private fun song(id: String) =
        Song(id = id, title = id, albumId = null, album = null, artist = null, artistId = null)

    private val downloaded = DownloadedLibrary(
        songIds = setOf("s2"),
        albumIds = setOf("al2", "al3"),
        artistIds = setOf("ar1"),
    )

    @Test
    fun `online every album is shown`() {
        val albums = listOf(album("al1"), album("al2"))

        assertThat(OfflineLibrary.albums(albums, isOffline = false, downloaded)).isEqualTo(albums)
    }

    @Test
    fun `offline only albums with a downloaded song are shown, in order`() {
        val albums = listOf(album("al3"), album("al1"), album("al2"))

        assertThat(OfflineLibrary.albums(albums, isOffline = true, downloaded).map { it.id })
            .containsExactly("al3", "al2")
            .inOrder()
    }

    @Test
    fun `offline search keeps downloaded songs, albums and artists only`() {
        val result = SearchResult(
            artists = listOf(artist("ar1"), artist("ar2")).toImmutableList(),
            albums = listOf(album("al1"), album("al2")).toImmutableList(),
            songs = listOf(song("s1"), song("s2")).toImmutableList(),
            includesServerResults = false,
        )

        val filtered = OfflineLibrary.search(result, isOffline = true, downloaded)

        assertThat(filtered.artists.map { it.id }).containsExactly("ar1")
        assertThat(filtered.albums.map { it.id }).containsExactly("al2")
        assertThat(filtered.songs.map { it.id }).containsExactly("s2")
        assertThat(filtered.includesServerResults).isFalse()
    }

    @Test
    fun `online search is untouched`() {
        val result = SearchResult(
            artists = persistentListOf(artist("ar2")),
            albums = persistentListOf(album("al1")),
            songs = persistentListOf(song("s1")),
            includesServerResults = true,
        )

        assertThat(OfflineLibrary.search(result, isOffline = false, downloaded)).isEqualTo(result)
    }

    @Test
    fun `shuffle albums offline picks from downloaded albums and queues downloaded songs`() {
        val albumIds = listOf("al1", "al2", "al3")
        val songs = listOf(song("s1"), song("s2"))

        assertThat(OfflineLibrary.shuffleAlbumIds(albumIds, downloaded)).containsExactly("al2", "al3").inOrder()
        assertThat(OfflineLibrary.shuffleSongs(songs, downloaded).map { it.id }).containsExactly("s2")
    }

    @Test
    fun `shuffle albums online uses everything`() {
        val albumIds = listOf("al1", "al2")
        val songs = listOf(song("s1"), song("s2"))

        assertThat(OfflineLibrary.shuffleAlbumIds(albumIds, offline = null)).isEqualTo(albumIds)
        assertThat(OfflineLibrary.shuffleSongs(songs, offline = null)).isEqualTo(songs)
    }
}
