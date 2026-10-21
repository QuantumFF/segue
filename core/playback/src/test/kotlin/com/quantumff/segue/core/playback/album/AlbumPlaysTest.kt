package com.quantumff.segue.core.playback.album

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.QueueEditor
import com.quantumff.segue.core.playback.service.SeguePlayer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumPlaysTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private val player = SeguePlayer(exo)

    /** 60 albums `al1`..`al60` of 10 tracks each; the view lists them `al1` first. */
    private val albumIds = (1..60).map { "al$it" }
    private val library = albumIds.associateWith { album -> (1..10).map { song("$album-t$it", album, it) } }
    private val albums = FakeAlbumShuffleRepository(library, albumIds)
    private val view = AlbumSource.Albums(AlbumSortOrder.Name)

    @After
    fun tearDown() = exo.release()

    private fun song(id: String, album: String?, track: Int = 1) =
        Song(id = id, title = id, albumId = album, album = album, artist = null, artistId = null, track = track)

    private fun plays(offline: DownloadedLibrary? = null, seed: Long = 1) =
        AlbumPlays(QueueEditor(player, offline = { offline }), albums, offline = { offline }, newSeed = { seed })

    private fun request(order: AlbumOrder, placement: QueuePlacement = QueuePlacement.Replace) =
        AlbumPlayRequest(view, order, placement)

    private fun queueIds() = List(exo.mediaItemCount) { exo.getMediaItemAt(it).mediaId }

    private fun albumsOf(ids: List<String>) = ids.map { it.substringBefore("-") }.distinct()

    /** Whether each album's tracks sit together, in track order. */
    private fun albumsWholeInTrackOrder(ids: List<String>) =
        ids.chunked(10).all { tracks -> tracks == (1..10).map { "${albumsOf(tracks).single()}-t$it" } }

    @Test
    fun `play albums replaces the queue with every album, in view order, each whole, and starts playing`() = runTest {
        player.setMediaItems(listOf(song("x", null).toMediaItem()))

        assertThat(plays().start(request(AlbumOrder.InOrder))).isTrue()

        assertThat(exo.playWhenReady).isTrue()
        assertThat(exo.mediaItemCount).isEqualTo(600)
        assertThat(albumsOf(queueIds())).isEqualTo(albumIds)
        assertThat(albumsWholeInTrackOrder(queueIds())).isTrue()
    }

    @Test
    fun `shuffle albums queues every album whole, in a shuffled album order that follows the seed`() = runTest {
        plays(seed = 99).start(request(AlbumOrder.Shuffled))
        val first = queueIds()

        assertThat(first).hasSize(600)
        assertThat(albumsOf(first)).containsExactlyElementsIn(albumIds)
        assertThat(albumsOf(first)).isNotEqualTo(albumIds)
        assertThat(albumsWholeInTrackOrder(first)).isTrue()

        plays(seed = 99).start(request(AlbumOrder.Shuffled))
        assertThat(queueIds()).isEqualTo(first)
    }

    @Test
    fun `shuffle all queues every song of the library once, albums spread out`() = runTest {
        plays(seed = 3).start(AlbumPlayRequest(AlbumSource.Library, AlbumOrder.ShuffledTracks))

        assertThat(queueIds()).containsExactlyElementsIn(library.values.flatten().map { it.id })
        // Songs, not whole albums: the first ten tracks are not one album.
        assertThat(albumsOf(queueIds().take(10)).size).isGreaterThan(1)
    }

    @Test
    fun `add to queue appends every album after the queue`() = runTest {
        player.setMediaItems(listOf("x1", "x2", "x3").map { song(it, "x").toMediaItem() })

        plays().start(request(AlbumOrder.Shuffled, QueuePlacement.End))

        assertThat(queueIds().take(3)).containsExactly("x1", "x2", "x3").inOrder()
        assertThat(albumsOf(queueIds().drop(3))).containsExactlyElementsIn(albumIds)
        assertThat(albumsWholeInTrackOrder(queueIds().drop(3))).isTrue()
    }

    @Test
    fun `play next inserts every album after the current track, before the rest of the queue`() = runTest {
        player.setMediaItems(listOf("x1", "x2", "x3").map { song(it, "x").toMediaItem() })

        plays().start(request(AlbumOrder.InOrder, QueuePlacement.Next))

        assertThat(queueIds().first()).isEqualTo("x1")
        assertThat(albumsOf(queueIds().subList(1, 601))).isEqualTo(albumIds)
        assertThat(queueIds().takeLast(2)).containsExactly("x2", "x3").inOrder()
    }

    @Test
    fun `a media session gets the songs requested with it, then every album, and queues them itself`() = runTest {
        val items = plays().items(
            AlbumPlayRequest(AlbumSource.Library, AlbumOrder.Shuffled),
            songs = listOf(song("y", null).toMediaItem()),
        )

        assertThat(items.first().mediaId).isEqualTo("y")
        assertThat(albumsOf(items.drop(1).map { it.mediaId })).containsExactlyElementsIn(albumIds)
        assertThat(exo.mediaItemCount).isEqualTo(0)
    }

    @Test
    fun `songs requested with shuffle albums are kept even when there are no albums`() = runTest {
        albums.ids = emptyList()

        val items = plays().items(request(AlbumOrder.Shuffled), songs = listOf(song("y", null).toMediaItem()))

        assertThat(items.map { it.mediaId }).containsExactly("y")
    }

    @Test
    fun `offline only downloaded songs of downloaded albums are queued`() = runTest {
        val downloaded =
            DownloadedLibrary(songIds = setOf("al2-t1", "al2-t2", "al5-t3"), albumIds = setOf("al2", "al5"))

        plays(offline = downloaded).start(request(AlbumOrder.InOrder))

        assertThat(queueIds()).containsExactly("al2-t1", "al2-t2", "al5-t3").inOrder()
    }

    @Test
    fun `offline shuffle all queues only downloaded songs`() = runTest {
        val downloaded = DownloadedLibrary(songIds = setOf("al2-t1", "al9-t4", "al40-t10"), albumIds = setOf("al2"))

        plays(offline = downloaded).start(AlbumPlayRequest(AlbumSource.Library, AlbumOrder.ShuffledTracks))

        assertThat(queueIds()).containsExactly("al2-t1", "al9-t4", "al40-t10")
    }

    @Test
    fun `nothing to play leaves the queue alone`() = runTest {
        player.setMediaItems(listOf(song("x1", "x").toMediaItem()))
        albums.ids = emptyList()

        assertThat(plays().start(request(AlbumOrder.InOrder))).isFalse()
        assertThat(queueIds()).containsExactly("x1")
    }
}

class FakeAlbumShuffleRepository(private val library: Map<String, List<Song>>, var ids: List<String>) :
    AlbumShuffleRepository {
    override suspend fun albumIds(source: AlbumSource): List<String> = ids

    override suspend fun songs(source: AlbumSource, albumIds: List<String>): List<Song> =
        albumIds.flatMap { library[it].orEmpty() }.reversed()

    override suspend fun allSongs(source: AlbumSource): List<Song> = ids.flatMap { library[it].orEmpty() }.reversed()
}
