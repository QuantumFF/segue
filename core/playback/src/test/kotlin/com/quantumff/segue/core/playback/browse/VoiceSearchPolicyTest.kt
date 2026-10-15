package com.quantumff.segue.core.playback.browse

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class VoiceSearchPolicyTest {
    private val radiohead = TestData.artist("ar-1", name = "Radiohead")
    private val inRainbows = TestData.album("al-1", name = "In Rainbows")
    private val radioheadAlbum = TestData.album("al-2", name = "Radiohead")
    private val nude = TestData.song("so-1", title = "Nude")

    private fun result(
        artists: List<com.quantumff.segue.core.model.Artist> = emptyList(),
        albums: List<com.quantumff.segue.core.model.Album> = emptyList(),
        songs: List<com.quantumff.segue.core.model.Song> = emptyList(),
    ) = SearchResult(
        artists = artists.toImmutableList(),
        albums = albums.toImmutableList(),
        songs = songs.toImmutableList(),
        includesServerResults = true,
    )

    @Test
    fun `an artist focus plays the artist`() {
        val target = VoiceSearchPolicy.choose(
            "radiohead",
            SearchFocus.Artist,
            result(listOf(radiohead), songs = listOf(nude)),
        )
        assertThat(target).isEqualTo(VoiceTarget.Artist("ar-1"))
    }

    @Test
    fun `an album focus plays the album`() {
        val target = VoiceSearchPolicy.choose(
            "in rainbows",
            SearchFocus.Album,
            result(listOf(radiohead), listOf(inRainbows)),
        )
        assertThat(target).isEqualTo(VoiceTarget.Album("al-1"))
    }

    @Test
    fun `a song focus plays the song`() {
        val target = VoiceSearchPolicy.choose(
            "nude",
            SearchFocus.Song,
            result(albums = listOf(inRainbows), songs = listOf(nude)),
        )
        assertThat(target).isEqualTo(VoiceTarget.Song("so-1"))
    }

    @Test
    fun `without a focus an exact artist name wins over an album of the same name`() {
        val target = VoiceSearchPolicy.choose(
            " RADIOHEAD ",
            SearchFocus.Any,
            result(listOf(radiohead), listOf(radioheadAlbum)),
        )
        assertThat(target).isEqualTo(VoiceTarget.Artist("ar-1"))
    }

    @Test
    fun `without a focus an exact album title wins over loose matches`() {
        val target = VoiceSearchPolicy.choose(
            "in rainbows",
            SearchFocus.Any,
            result(listOf(radiohead), listOf(radioheadAlbum, inRainbows), listOf(nude)),
        )
        assertThat(target).isEqualTo(VoiceTarget.Album("al-1"))
    }

    @Test
    fun `without an exact match the top song plays, then the top album, then the top artist`() {
        assertThat(
            VoiceSearchPolicy.choose("x", SearchFocus.Any, result(listOf(radiohead), listOf(inRainbows), listOf(nude))),
        )
            .isEqualTo(VoiceTarget.Song("so-1"))
        assertThat(VoiceSearchPolicy.choose("x", SearchFocus.Any, result(listOf(radiohead), listOf(inRainbows))))
            .isEqualTo(VoiceTarget.Album("al-1"))
        assertThat(VoiceSearchPolicy.choose("x", SearchFocus.Any, result(listOf(radiohead))))
            .isEqualTo(VoiceTarget.Artist("ar-1"))
    }

    @Test
    fun `a focus with no matching kind falls back to any match`() {
        assertThat(VoiceSearchPolicy.choose("nude", SearchFocus.Artist, result(songs = listOf(nude))))
            .isEqualTo(VoiceTarget.Song("so-1"))
    }

    @Test
    fun `no results play nothing`() {
        assertThat(
            VoiceSearchPolicy.choose(
                "x",
                SearchFocus.Any,
                SearchResult(persistentListOf(), persistentListOf(), persistentListOf(), true),
            ),
        )
            .isNull()
    }

    @Test
    fun `focus comes from the media focus extra`() {
        assertThat(SearchFocus.fromMimeType("vnd.android.cursor.item/artist")).isEqualTo(SearchFocus.Artist)
        assertThat(SearchFocus.fromMimeType("vnd.android.cursor.item/album")).isEqualTo(SearchFocus.Album)
        assertThat(SearchFocus.fromMimeType("vnd.android.cursor.item/audio")).isEqualTo(SearchFocus.Song)
        assertThat(SearchFocus.fromMimeType(null)).isEqualTo(SearchFocus.Any)
    }
}
