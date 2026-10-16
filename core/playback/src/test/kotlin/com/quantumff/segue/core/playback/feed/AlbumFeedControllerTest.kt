package com.quantumff.segue.core.playback.feed

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.repository.AlbumFeedRepository
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.model.AlbumFeed
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.SeguePlayer
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AlbumFeedControllerTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private var controller: AlbumFeedController? = null
    private val player = SeguePlayer(exo, Random(3)) { controller?.onQueueChanged() }

    /** 60 albums `al1`..`al60` of 10 tracks each; the view lists them `al1` first. */
    private val albumIds = (1..60).map { "al$it" }
    private val library = albumIds.associateWith { album -> (1..10).map { song("$album-t$it", album, it) } }
    private val albums = FakeAlbumShuffleRepository(library, albumIds)
    private val feeds = FakeAlbumFeedRepository()
    private val view = AlbumSource.Albums(AlbumSortOrder.Name)

    @After
    fun tearDown() = exo.release()

    private fun song(id: String, album: String?, track: Int = 1) =
        Song(id = id, title = id, albumId = album, album = album, artist = null, artistId = null, track = track)

    private fun TestScope.controller(offline: DownloadedLibrary? = null) =
        AlbumFeedController(player, albums, feeds, this, offline = { offline }).also {
            controller = it
            exo.addListener(it)
        }

    private fun queueIds() = List(exo.mediaItemCount) { exo.getMediaItemAt(it).mediaId }

    private fun playOrderIds() = player.playOrder().map { exo.getMediaItemAt(it).mediaId }

    private fun albumsInPlayOrder() = playOrderIds().map { it.substringBefore("-") }.distinct()

    private fun request(order: AlbumOrder, placement: QueuePlacement = QueuePlacement.Replace) =
        AlbumPlayRequest(view, order, placement)

    @Test
    fun `play albums replaces the queue with the first window, in view order, and starts playing`() =
        runTest(UnconfinedTestDispatcher()) {
            player.setMediaItems(listOf(song("x", null).toMediaItem()))
            player.setShuffleMode(ShuffleMode.Tracks)
            val feed = controller()

            assertThat(feed.start(request(AlbumOrder.InOrder), seed = 1)).isTrue()

            assertThat(player.shuffleMode).isEqualTo(ShuffleMode.Off)
            assertThat(exo.playWhenReady).isTrue()
            assertThat(exo.mediaItemCount).isEqualTo(AlbumFeedPolicy.WINDOW_TRACKS)
            assertThat(albumsInPlayOrder()).isEqualTo(albumIds.take(20))
            assertThat(queueIds().take(11)).isEqualTo((1..10).map { "al1-t$it" } + "al2-t1")
            assertThat(feeds.stored?.cursor).isEqualTo(20)
            assertThat(feeds.stored?.queueFingerprint).isEqualTo(AlbumFeedPolicy.fingerprint(playOrderIds()))
        }

    @Test
    fun `shuffle albums follows its seed and keeps every album in track order`() = runTest(UnconfinedTestDispatcher()) {
        controller().start(request(AlbumOrder.Shuffled), seed = 99)

        val expected = AlbumFeedOrder.order(albumIds, AlbumOrder.Shuffled, 99).take(20)
        assertThat(albumsInPlayOrder()).isEqualTo(expected)
        assertThat(queueIds().take(10)).isEqualTo((1..10).map { "${expected.first()}-t$it" })
        assertThat(feeds.stored?.seed).isEqualTo(99)
    }

    @Test
    fun `near the end of what is queued the feed appends the next albums and saves its cursor`() =
        runTest(UnconfinedTestDispatcher()) {
            controller().start(request(AlbumOrder.InOrder), seed = 1)

            player.seekTo(185, 0)
            advanceUntilIdle()

            assertThat(exo.mediaItemCount).isEqualTo(AlbumFeedPolicy.WINDOW_TRACKS + AlbumFeedPolicy.EXTENSION_TRACKS)
            assertThat(albumsInPlayOrder()).isEqualTo(albumIds.take(30))
            assertThat(feeds.stored?.cursor).isEqualTo(30)
            assertThat(feeds.stored?.queueFingerprint).isEqualTo(AlbumFeedPolicy.fingerprint(playOrderIds()))
        }

    @Test
    fun `the feed ends when the source runs out`() = runTest(UnconfinedTestDispatcher()) {
        albums.ids = albumIds.take(21)
        controller().start(request(AlbumOrder.InOrder), seed = 1)
        player.seekTo(195, 0)
        advanceUntilIdle()

        assertThat(exo.mediaItemCount).isEqualTo(210)
        assertThat(feeds.stored).isNull()
        assertThat(controller?.current).isNull()
    }

    @Test
    fun `any other edit of the queue stops the feed for good`() = runTest(UnconfinedTestDispatcher()) {
        val feed = controller()
        feed.start(request(AlbumOrder.InOrder), seed = 1)

        player.removeMediaItem(3)
        player.seekTo(190, 0)
        advanceUntilIdle()

        assertThat(feed.current).isNull()
        assertThat(feeds.stored).isNull()
        assertThat(exo.mediaItemCount).isEqualTo(199)
    }

    @Test
    fun `a shuffle change stops the feed, a favorite toggle does not`() = runTest(UnconfinedTestDispatcher()) {
        val feed = controller()
        feed.start(request(AlbumOrder.InOrder), seed = 1)

        player.replaceMediaItem(0, exo.getMediaItemAt(0))
        assertThat(feed.current).isNotNull()

        player.setShuffleMode(ShuffleMode.Tracks)
        assertThat(feed.current).isNull()
    }

    @Test
    fun `add to queue appends whole albums after the queue, in order, even in track shuffle`() =
        runTest(UnconfinedTestDispatcher()) {
            player.setMediaItems(listOf("x1", "x2", "x3").map { song(it, "x").toMediaItem() })
            player.setShuffleMode(ShuffleMode.Tracks)

            controller().start(request(AlbumOrder.Shuffled, QueuePlacement.End), seed = 5)

            val expected = AlbumFeedOrder.order(albumIds, AlbumOrder.Shuffled, 5).take(20)
            assertThat(playOrderIds().take(3)).containsExactly("x1", "x2", "x3")
            assertThat(playOrderIds().drop(3).map { it.substringBefore("-") }.distinct()).isEqualTo(expected)
            assertThat(playOrderIds().drop(3).take(10)).isEqualTo((1..10).map { "${expected.first()}-t$it" })
            assertThat(player.shuffleMode).isEqualTo(ShuffleMode.Tracks)
            // The appended part is the end of the queue, so the feed keeps going.
            assertThat(feeds.stored).isNotNull()
        }

    @Test
    fun `play next inserts after the current track and does not extend past the rest of the queue`() =
        runTest(UnconfinedTestDispatcher()) {
            player.setMediaItems(listOf("x1", "x2", "x3").map { song(it, "x").toMediaItem() })

            val feed = controller()
            feed.start(request(AlbumOrder.InOrder, QueuePlacement.Next), seed = 1)

            assertThat(queueIds().first()).isEqualTo("x1")
            assertThat(queueIds().subList(1, 11)).isEqualTo((1..10).map { "al1-t$it" })
            assertThat(queueIds().takeLast(2)).containsExactly("x2", "x3").inOrder()
            assertThat(feed.current).isNull()
            assertThat(feeds.stored).isNull()
        }

    @Test
    fun `play next with nothing after the current track keeps the feed`() = runTest(UnconfinedTestDispatcher()) {
        player.setMediaItems(listOf(song("x1", "x").toMediaItem()))

        controller().start(request(AlbumOrder.InOrder, QueuePlacement.Next), seed = 1)

        assertThat(queueIds().first()).isEqualTo("x1")
        assertThat(feeds.stored).isNotNull()
    }

    @Test
    fun `a saved feed resumes only on the queue it left`() = runTest(UnconfinedTestDispatcher()) {
        controller().start(request(AlbumOrder.InOrder), seed = 1)
        val saved = feeds.stored!!

        // Process death: a fresh controller restores against the restored queue.
        exo.removeListener(controller!!)
        val restored = controller()
        restored.restore()
        assertThat(restored.current).isEqualTo(saved)
        player.seekTo(190, 0)
        advanceUntilIdle()
        assertThat(albumsInPlayOrder()).isEqualTo(albumIds.take(30))

        // A queue that differs (a song went missing on restore) drops the feed.
        feeds.stored = saved
        exo.removeListener(restored)
        player.removeMediaItem(0)
        val mismatched = controller()
        mismatched.restore()
        assertThat(mismatched.current).isNull()
        assertThat(feeds.stored).isNull()
    }

    @Test
    fun `offline only downloaded songs of downloaded albums are queued`() = runTest(UnconfinedTestDispatcher()) {
        val downloaded =
            DownloadedLibrary(songIds = setOf("al2-t1", "al2-t2", "al5-t3"), albumIds = setOf("al2", "al5"))

        controller(offline = downloaded).start(request(AlbumOrder.InOrder), seed = 1)

        assertThat(queueIds()).containsExactly("al2-t1", "al2-t2", "al5-t3").inOrder()
    }

    @Test
    fun `nothing to play leaves the queue alone`() = runTest(UnconfinedTestDispatcher()) {
        player.setMediaItems(listOf(song("x1", "x").toMediaItem()))
        albums.ids = emptyList()

        assertThat(controller().start(request(AlbumOrder.InOrder), seed = 1)).isFalse()
        assertThat(queueIds()).containsExactly("x1")
    }
}

class FakeAlbumShuffleRepository(private val library: Map<String, List<Song>>, var ids: List<String>) :
    AlbumShuffleRepository {
    override suspend fun albumIds(source: AlbumSource): List<String> = ids

    override suspend fun songs(source: AlbumSource, albumIds: List<String>): List<Song> =
        albumIds.flatMap { library[it].orEmpty() }.reversed()
}

class FakeAlbumFeedRepository : AlbumFeedRepository {
    var stored: AlbumFeed? = null

    override suspend fun feed(): AlbumFeed? = stored

    override suspend fun save(feed: AlbumFeed) {
        stored = feed
    }

    override suspend fun clear() {
        stored = null
    }
}
