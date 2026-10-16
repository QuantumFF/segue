package com.quantumff.segue.core.playback.browse

import android.net.Uri
import android.os.Bundle
import androidx.media3.session.MediaConstants
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumSource
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
    private val tree = BrowseTree(
        repository = repo,
        albumShuffle = object : AlbumShuffleRepository {
            override suspend fun albumIds(source: AlbumSource) = repo.albums.map { it.id }

            override suspend fun songs(source: AlbumSource, albumIds: List<String>): List<Song> =
                repo.songs.filter { it.albumId in albumIds }
        },
        labels = BrowseLabels("H", "L", "D", "S", "RP", "RA", "St", "Al", "Ar", "Pl", "G"),
        artwork = object : BrowseArtwork {
            override fun cover(coverArtId: String): Uri = Uri.parse("content://art/$coverArtId")

            override fun icon(icon: BrowseIcon): Uri? = null
        },
    )
    private val search = LibrarySearch(
        searchRepository,
        repo,
        tree,
        SearchLabels(artists = "Artists", albums = "Albums", songs = "Songs"),
    )

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
    fun `results list artists, albums, then songs under group titles, using the server results`() = runTest {
        searchRepository.results["radiohead"] = results(
            result(songs = listOf(nude), server = false),
            result(listOf(radiohead), listOf(inRainbows), listOf(nude)),
        )

        val items = search.results("radiohead")

        assertThat(items.map { BrowseId.parse(it.mediaId) }).containsExactly(
            BrowseId.Artist("ar-1"),
            BrowseId.Album("al-1"),
            BrowseId.Track("so-1", null, 0),
        ).inOrder()
        assertThat(
            items.map {
                it.mediaMetadata.extras?.getString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE)
            },
        )
            .containsExactly("Artists", "Albums", "Songs").inOrder()
    }

    @Test
    fun `play an artist queues their albums in shuffled track order`() = runTest {
        repo.artists = listOf(radiohead)
        repo.albums = listOf(inRainbows)
        repo.songs = listOf(nude, reckoner)
        searchRepository.results["radiohead"] = results(result(listOf(radiohead)))

        val request = search.playRequest("radiohead", focusExtras("vnd.android.cursor.item/artist"))!!

        assertThat(request.songs.map { it.id }).containsExactly("so-1", "so-2")
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
    fun `an empty request shuffles albums`() = runTest {
        repo.albums = listOf(inRainbows)
        repo.songs = listOf(nude)

        val request = search.playRequest("", Bundle.EMPTY)!!

        assertThat(request.songs.map { it.id }).containsExactly("so-1")
    }

    @Test
    fun `no match plays nothing`() = runTest {
        assertThat(search.playRequest("zzz", Bundle.EMPTY)).isNull()
    }

    private fun focusExtras(mime: String) = Bundle().apply { putString("android.intent.extra.focus", mime) }
}
