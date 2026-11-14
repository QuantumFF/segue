package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class ArtistAlbumsTest {
    private fun album(id: String, year: Int?, vararg releaseTypes: String, artistId: String = "ar") = Album(
        id = id,
        name = id,
        artist = null,
        artistId = artistId,
        artists = persistentListOf(),
        coverArtId = null,
        songCount = 1,
        durationSeconds = 0,
        year = year,
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
        releaseTypes = releaseTypes.toList().toImmutableList(),
    )

    @Test
    fun `groups the artist's own albums by primary release type and keeps appears on apart`() {
        val grouped = ArtistAlbums.of(
            "ar",
            listOf(
                album("single", 2021, "single"),
                album("lp", 2020, "album"),
                album("guest", 2019, "album", artistId = "other"),
                album("ep", 2018, "EP"),
                album("best-of", 2017, "compilation"),
                // The primary type is the first one: an album that is also a compilation is an album.
                album("live-lp", 2016, "album", "compilation"),
            ),
        )

        assertThat(grouped.albums.map { it.id }).containsExactly("lp", "live-lp").inOrder()
        assertThat(grouped.epsAndSingles.map { it.id }).containsExactly("single", "ep").inOrder()
        assertThat(grouped.compilations.map { it.id }).containsExactly("best-of")
        assertThat(grouped.appearsOn.map { it.id }).containsExactly("guest")
        assertThat(grouped.inPageOrder.map { it.id })
            .containsExactly("lp", "live-lp", "single", "ep", "best-of", "guest").inOrder()
    }

    @Test
    fun `without release types the own albums are one albums group, newest first`() {
        val grouped = ArtistAlbums.of(
            "ar",
            listOf(album("old", 1999), album("undated", null), album("new", 2010), album("mid", 2005)),
        )

        assertThat(grouped.albums.map { it.id }).containsExactly("new", "mid", "old", "undated").inOrder()
        assertThat(grouped.epsAndSingles).isEmpty()
        assertThat(grouped.compilations).isEmpty()
    }

    @Test
    fun `an unknown release type counts as an album`() {
        val grouped = ArtistAlbums.of("ar", listOf(album("live", 2000, "live")))

        assertThat(grouped.albums.map { it.id }).containsExactly("live")
    }
}
