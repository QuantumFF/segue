package com.quantumff.segue.core.playback.browse

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeSearchRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibrarySearchTest {
    private val repo = FakeMediaBrowseRepository()
    private val searchRepository = FakeSearchRepository()
    private val search = LibrarySearch(searchRepository, repo)

    private val radiohead = TestData.artist("ar-1", name = "Radiohead")
    private val inRainbows = TestData.album("al-1", name = "In Rainbows", artistId = "ar-1")
    private val nude = TestData.song("so-1", title = "Nude", albumId = "al-1", track = 3)
    private val reckoner = TestData.song("so-2", title = "Reckoner", albumId = "al-1", track = 7)

    private fun results(vararg emissions: SearchResult) = emissions.toList()

    private fun result(
        artists: List<Artist> = emptyList(),
        albums: List<Album> = emptyList(),
        songs: List<Song> = emptyList(),
        server: Boolean = true,
    ) = SearchResult(
        artists.toImmutableList(),
        albums.toImmutableList(),
        songs.toImmutableList(),
        includesServerResults = server,
    )

    @Test
    fun `search uses the server results once they arrive`() = runTest {
        searchRepository.results["radiohead"] = results(
            result(songs = listOf(nude), server = false),
            result(listOf(radiohead), listOf(inRainbows), listOf(nude)),
        )

        val result = search.find("radiohead")

        assertThat(result.includesServerResults).isTrue()
        assertThat(result.artists).containsExactly(radiohead)
    }

    @Test
    fun `play an artist queues their albums in shuffled track order`() = runTest {
        repo.artists = listOf(radiohead)
        repo.albums = listOf(inRainbows)
        repo.songs = listOf(nude, reckoner)
        searchRepository.results["radiohead"] = results(result(listOf(radiohead)))

        val request = search.playRequest("radiohead", focusExtras("vnd.android.cursor.item/artist"))!!

        assertThat(request.songs.map { it.id }).containsExactly("so-1", "so-2")
        assertThat(request.shuffle).isTrue()
    }

    @Test
    fun `the focused field is searched instead of the whole utterance`() = runTest {
        repo.songs = listOf(nude, reckoner)
        searchRepository.results["in rainbows"] = results(result(albums = listOf(inRainbows)))

        val extras = focusExtras("vnd.android.cursor.item/album").apply {
            putString("android.intent.extra.album", "in rainbows")
        }
        val request = search.playRequest("in rainbows by radiohead", extras)!!

        assertThat(searchRepository.queries).containsExactly("in rainbows")
        assertThat(request.songs.map { it.id }).containsExactly("so-1", "so-2").inOrder()
    }

    @Test
    fun `play a song plays the song`() = runTest {
        repo.songs = listOf(nude)
        searchRepository.results["nude"] = results(result(songs = listOf(nude)))

        val request = search.playRequest("nude", Bundle.EMPTY)!!

        assertThat(request.songs.map { it.id }).containsExactly("so-1")
    }

    @Test
    fun `an empty request is not a fixed list`() = runTest {
        repo.songs = listOf(nude)

        assertThat(search.playRequest("", Bundle.EMPTY)).isNull()
    }

    @Test
    fun `no match plays nothing`() = runTest {
        assertThat(search.playRequest("zzz", Bundle.EMPTY)).isNull()
    }

    private fun focusExtras(mime: String) = Bundle().apply { putString("android.intent.extra.focus", mime) }
}
