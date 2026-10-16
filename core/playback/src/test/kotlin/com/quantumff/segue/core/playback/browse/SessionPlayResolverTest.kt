package com.quantumff.segue.core.playback.browse

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeSearchRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionPlayResolverTest {
    private val repo = FakeMediaBrowseRepository().apply {
        songs = listOf(
            TestData.song("a", albumId = "al-1", track = 1),
            TestData.song("b", albumId = "al-1", track = 2),
            TestData.song("c", albumId = "al-1", track = 3),
        )
    }
    private val searchRepository = FakeSearchRepository()
    private val tree = BrowseTree(
        repository = repo,
        labels = BrowseLabels("H", "L", "D", "S", "RP", "RA", "St", "Al", "Ar", "Pl", "G"),
        artwork = object : BrowseArtwork {
            override fun cover(coverArtId: String): Uri = Uri.parse("content://art/$coverArtId")

            override fun icon(icon: BrowseIcon): Uri? = null
        },
    )
    private val resolver = SessionPlayResolver(tree, LibrarySearch(searchRepository, repo))

    private val shuffleLibrary = AlbumPlayRequest(AlbumSource.Library, AlbumOrder.Shuffled)

    private fun node(id: BrowseId) = MediaItem.Builder().setMediaId(id.encode()).build()

    private fun song(id: String) = MediaItem.Builder().setMediaId(id).build()

    private fun voice(query: String) = MediaItem.Builder()
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(query).setExtras(Bundle()).build())
        .build()

    private fun SessionPlay.ids() = (this as SessionPlay.Items).items.map { it.mediaId }

    private suspend fun set(item: MediaItem, allowed: Boolean = true, hasQueue: Boolean = false) =
        resolver.resolveSet(listOf(item), startIndex = 0, startPositionMs = 0, allowed, hasQueue)

    @Test
    fun `plain songs pass through for any controller, from the requested start`() = runTest {
        val items = listOf(song("x"), song("y"))

        val set = resolver.resolveSet(items, startIndex = 1, startPositionMs = 4_000, allowed = false, hasQueue = true)
        assertThat(set).isEqualTo(SessionPlay.Items(items, startIndex = 1, startPositionMs = 4_000))

        // Media3's "reset position" stays unset.
        val reset = resolver.resolveSet(items, C.INDEX_UNSET, C.TIME_UNSET, allowed = true, hasQueue = false)
        assertThat((reset as SessionPlay.Items).startIndex).isEqualTo(C.INDEX_UNSET)

        assertThat(resolver.resolveAdd(items, allowed = false)).isEqualTo(SessionPlay.Items(items))
    }

    @Test
    fun `only allowed browsers may play browse nodes or voice requests`() = runTest {
        val track = node(BrowseId.Track("b", BrowseId.Album("al-1"), 1))

        assertThat(set(track, allowed = false)).isEqualTo(SessionPlay.NotAllowed)
        assertThat(set(voice("nude"), allowed = false)).isEqualTo(SessionPlay.NotAllowed)
        assertThat(resolver.resolveAdd(listOf(track), allowed = false)).isEqualTo(SessionPlay.NotAllowed)
        assertThat(resolver.resolveAdd(listOf(song("x"), track), allowed = false)).isEqualTo(SessionPlay.NotAllowed)
    }

    @Test
    fun `playing a track plays its list from it, adding it adds only the track`() = runTest {
        val track = node(BrowseId.Track("b", BrowseId.Album("al-1"), 1))

        val played = set(track) as SessionPlay.Items
        assertThat(played.ids()).containsExactly("a", "b", "c").inOrder()
        assertThat(played.startIndex).isEqualTo(1)

        assertThat(resolver.resolveAdd(listOf(track), allowed = true).ids()).containsExactly("b")
        // Every node in a longer list is expanded; plain songs stay.
        assertThat(resolver.resolveAdd(listOf(node(BrowseId.Album("al-1")), song("x")), allowed = true).ids())
            .containsExactly("a", "b", "c", "x").inOrder()
    }

    @Test
    fun `shuffle albums starts a library album feed, appended when added`() = runTest {
        val shuffleAlbums = node(BrowseId.ShuffleAlbums)

        assertThat(set(shuffleAlbums)).isEqualTo(SessionPlay.AlbumFeed(shuffleLibrary))
        assertThat(resolver.resolveAdd(listOf(shuffleAlbums), allowed = true))
            .isEqualTo(SessionPlay.AlbumFeed(shuffleLibrary.copy(placement = QueuePlacement.End)))
    }

    @Test
    fun `voice play music shuffles albums, or with a queue keeps it`() = runTest {
        assertThat(set(voice(""), hasQueue = false)).isEqualTo(SessionPlay.AlbumFeed(shuffleLibrary))
        assertThat(set(voice(" "), hasQueue = true)).isEqualTo(SessionPlay.KeepQueue)
    }

    @Test
    fun `a voice request for an artist is queued shuffled`() = runTest {
        repo.albums = listOf(TestData.album("al-1", artistId = "ar-1"))
        searchRepository.results["radiohead"] = listOf(
            SearchResult(
                persistentListOf(TestData.artist("ar-1", name = "Radiohead")),
                persistentListOf(),
                persistentListOf(),
                includesServerResults = true,
            ),
        )

        val play = set(voice("radiohead")) as SessionPlay.Items

        assertThat(play.ids()).containsExactly("a", "b", "c")
        assertThat(play.shuffle).isTrue()
    }

    @Test
    fun `nothing to play when a node or a voice request matches nothing`() = runTest {
        assertThat(set(node(BrowseId.Album("missing")))).isEqualTo(SessionPlay.NothingToPlay)
        assertThat(set(node(BrowseId.Library))).isEqualTo(SessionPlay.NothingToPlay)
        assertThat(set(voice("zzz"))).isEqualTo(SessionPlay.NothingToPlay)
        assertThat(resolver.resolveAdd(listOf(node(BrowseId.Album("missing"))), allowed = true))
            .isEqualTo(SessionPlay.NothingToPlay)
    }
}
