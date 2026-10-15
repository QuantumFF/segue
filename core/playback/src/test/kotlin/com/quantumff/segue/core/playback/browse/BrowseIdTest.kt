package com.quantumff.segue.core.playback.browse

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrowseIdTest {
    private val all = listOf(
        BrowseId.Root,
        BrowseId.Home,
        BrowseId.Library,
        BrowseId.Downloads,
        BrowseId.ShuffleAlbums,
        BrowseId.RecentlyPlayed,
        BrowseId.RecentlyAdded,
        BrowseId.Starred,
        BrowseId.Albums,
        BrowseId.Artists,
        BrowseId.Playlists,
        BrowseId.Genres,
        BrowseId.Album("al-1"),
        BrowseId.Artist("ar-1"),
        BrowseId.Playlist("pl-1"),
        BrowseId.Genre("Rock/Pop & Folk"),
        BrowseId.Track("so-1", parent = BrowseId.Album("al-1"), index = 3),
        BrowseId.Track("so-1", parent = BrowseId.Playlist("p/l"), index = 0),
        BrowseId.Track("so-1", parent = BrowseId.Starred, index = 12),
        BrowseId.Track("so-1", parent = null, index = 0),
    )

    @Test
    fun `every id survives a round trip`() {
        all.forEach { id -> assertThat(BrowseId.parse(id.encode())).isEqualTo(id) }
    }

    @Test
    fun `ids are unique`() {
        assertThat(all.map { it.encode() }.toSet()).hasSize(all.size)
    }

    @Test
    fun `browse ids never look like a song id`() {
        all.forEach { assertThat(it.encode()).startsWith("/") }
    }

    @Test
    fun `a plain song id or garbage is not a browse id`() {
        assertThat(BrowseId.parse("3fa2c1d0e9b8")).isNull()
        assertThat(BrowseId.parse("/nope")).isNull()
        assertThat(BrowseId.parse("/album")).isNull()
        assertThat(BrowseId.parse("/track/so-1/x/%2Falbum%2Fal")).isNull()
        assertThat(BrowseId.parse("")).isNull()
    }

    @Test
    fun `odd characters in names are escaped`() {
        val encoded = BrowseId.Genre("a/b c%").encode()
        assertThat(encoded.count { it == '/' }).isEqualTo(2)
    }
}
