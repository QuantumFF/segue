package com.quantumff.segue.core.playback.service

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.media.albumKey
import com.quantumff.segue.core.playback.media.isStarred
import com.quantumff.segue.core.playback.media.replayGain
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.media.toQueueItem
import com.quantumff.segue.core.playback.media.withPlayableUri
import com.quantumff.segue.core.playback.media.withStarred
import kotlin.random.Random
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeguePlayerTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private val modes = mutableListOf<ShuffleMode>()
    private val player = SeguePlayer(exo, Random(5)) { modes += it }

    @After
    fun tearDown() = exo.release()

    private fun song(id: String, album: String?) =
        Song(id = id, title = id, albumId = album, album = album, artist = null, artistId = null)

    private fun album(name: String, tracks: Int) = (1..tracks).map { song("$name$it", name) }

    private val queue = album("a", 3) + album("b", 2) + album("c", 4) + album("d", 1)

    private fun playOrderIds() = player.playOrder().map { exo.getMediaItemAt(it).mediaId }

    private fun assertAlbumsContiguous() {
        val albums = player.playOrder().map { exo.getMediaItemAt(it).albumKey }
        val runs = albums.zipWithNext().count { (x, y) -> x != y } + 1
        assertThat(runs).isEqualTo(albums.distinct().size)
    }

    @Test
    fun `album shuffle keeps albums whole and starts with the current album`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 6, 0)
        player.setShuffleMode(ShuffleMode.Albums)

        assertThat(exo.shuffleModeEnabled).isTrue()
        assertAlbumsContiguous()
        assertThat(playOrderIds().take(4)).containsExactly("c1", "c2", "c3", "c4").inOrder()
        assertThat(modes).containsExactly(ShuffleMode.Albums)
    }

    @Test
    fun `a new queue in album shuffle starts with the album of the start item`() {
        player.setShuffleMode(ShuffleMode.Albums)
        player.setMediaItems(queue.map { it.toMediaItem() }, 3, 0)

        assertAlbumsContiguous()
        assertThat(playOrderIds().take(2)).containsExactly("b1", "b2").inOrder()
        assertThat(exo.currentMediaItemIndex).isEqualTo(3)
    }

    @Test
    fun `items added in album shuffle get their album from the media items`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 0, 0)
        player.setShuffleMode(ShuffleMode.Albums)
        player.addMediaItems(album("e", 3).map { it.toMediaItem() })
        player.addMediaItems(1, album("f", 2).map { it.toMediaItem() })

        assertAlbumsContiguous()
        // "Play next" of another album plays right after the current album.
        assertThat(playOrderIds().take(5)).containsExactly("a1", "a2", "a3", "f1", "f2").inOrder()
        // Appended albums play last.
        assertThat(playOrderIds().takeLast(3)).containsExactly("e1", "e2", "e3").inOrder()
    }

    @Test
    fun `turning shuffle off plays the original queue order`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 0, 0)
        player.setShuffleMode(ShuffleMode.Albums)
        player.setShuffleMode(ShuffleMode.Off)

        assertThat(playOrderIds()).isEqualTo(queue.map { it.id })
        assertThat(exo.shuffleModeEnabled).isFalse()
    }

    @Test
    fun `track shuffle starts with the current track and play next plays next`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 4, 0)
        player.setShuffleMode(ShuffleMode.Tracks)
        assertThat(playOrderIds().first()).isEqualTo("b2")

        player.addMediaItems(5, listOf(song("x", "x").toMediaItem()))
        val order = playOrderIds()
        assertThat(order[order.indexOf("b2") + 1]).isEqualTo("x")
    }

    @Test
    fun `a generic shuffle toggle uses the preferred mode`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 0, 0)
        player.preferredShuffleMode = ShuffleMode.Albums
        player.shuffleModeEnabled = true
        assertThat(player.shuffleMode).isEqualTo(ShuffleMode.Albums)

        player.preferredShuffleMode = ShuffleMode.Off
        player.shuffleModeEnabled = false
        player.shuffleModeEnabled = true
        assertThat(player.shuffleMode).isEqualTo(ShuffleMode.Tracks)
    }

    @Test
    fun `the queue state carries the shuffle and uploads in effective play order`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 6, 1_000)
        player.setShuffleMode(ShuffleMode.Albums)

        val state = player.queueState()
        assertThat(state.songIds).isEqualTo(queue.map { it.id })
        assertThat(state.currentIndex).isEqualTo(6)
        assertThat(state.shuffleMode).isEqualTo(ShuffleMode.Albums)
        assertThat(state.playOrder).isEqualTo(player.playOrder())

        val uploaded = state.inPlayOrder()
        assertThat(uploaded.songIds).isEqualTo(playOrderIds())
        assertThat(uploaded.songIds[uploaded.currentIndex]).isEqualTo("c2")
        assertThat(uploaded.positionMillis).isEqualTo(1_000)
    }

    @Test
    fun `an unshuffled queue state has no play order`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 2, 0)
        assertThat(player.queueState().playOrder).isNull()
        assertThat(player.queueState().shuffleMode).isEqualTo(ShuffleMode.Off)
    }

    @Test
    fun `restoring a saved album shuffle resumes exactly, and inserts still keep albums whole`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 7, 0)
        player.setShuffleMode(ShuffleMode.Albums)
        player.moveInPlayOrder(player.playOrder().lastIndex, 4) // A user tweak a reshuffle would lose.
        val saved = player.queueState()
        val savedOrder = playOrderIds()

        val freshExo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
        try {
            val restoredModes = mutableListOf<ShuffleMode>()
            val restored = SeguePlayer(freshExo, Random(99)) { restoredModes += it }
            restored.restore(queue.map { it.toMediaItem() }, saved.copy(positionMillis = 4_000))

            assertThat(restored.shuffleMode).isEqualTo(ShuffleMode.Albums)
            assertThat(restoredModes).containsExactly(ShuffleMode.Albums)
            assertThat(restored.playOrder().map { freshExo.getMediaItemAt(it).mediaId }).isEqualTo(savedOrder)
            assertThat(freshExo.currentMediaItemIndex).isEqualTo(7)
            assertThat(freshExo.currentPosition).isEqualTo(4_000)
            assertThat(restored.queueState().copy(positionMillis = 0)).isEqualTo(saved.copy(positionMillis = 0))

            restored.addMediaItems(album("e", 2).map { it.toMediaItem() })
            val order = restored.playOrder().map { freshExo.getMediaItemAt(it).mediaId }
            assertThat(order.takeLast(2)).containsExactly("e1", "e2").inOrder()
        } finally {
            freshExo.release()
        }
    }

    @Test
    fun `restoring a track shuffle keeps its order`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 0, 0)
        player.setShuffleMode(ShuffleMode.Tracks)
        val saved = player.queueState()

        val freshExo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
        try {
            val restored = SeguePlayer(freshExo, Random(1))
            restored.restore(queue.map { it.toMediaItem() }, saved)
            assertThat(restored.shuffleMode).isEqualTo(ShuffleMode.Tracks)
            assertThat(restored.playOrder()).isEqualTo(saved.playOrder)
        } finally {
            freshExo.release()
        }
    }

    @Test
    fun `an item removed and reinserted returns to the same place in queue and play order`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 0, 0)
        player.setShuffleMode(ShuffleMode.Tracks)
        val before = playOrderIds()
        val timelineIndex = 5
        val item = exo.getMediaItemAt(timelineIndex)
        val playPosition = player.playOrder().indexOf(timelineIndex)

        player.removeMediaItem(timelineIndex)
        player.reinsert(item, timelineIndex, playPosition)

        assertThat(playOrderIds()).isEqualTo(before)
        assertThat((0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }).isEqualTo(queue.map { it.id })
    }

    @Test
    fun `moving an album block in play order moves all its tracks`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 0, 0)
        player.setShuffleMode(ShuffleMode.Albums)
        val before = playOrderIds()
        val blockStart = before.indexOfFirst { it.startsWith("c") }

        player.moveInPlayOrder(blockStart, to = 0, count = 4)

        assertThat(playOrderIds().take(4)).containsExactly("c1", "c2", "c3", "c4").inOrder()
        assertAlbumsContiguous()
    }

    @Test
    fun `moving in play order with shuffle on reorders only the play order`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 0, 0)
        player.setShuffleMode(ShuffleMode.Albums)
        val before = playOrderIds()

        player.moveInPlayOrder(before.lastIndex, 1)

        val after = playOrderIds()
        assertThat(after[1]).isEqualTo(before.last())
        assertThat((0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }).isEqualTo(queue.map { it.id })
    }

    @Test
    fun `media items round-trip what the player needs`() {
        val gain = ReplayGain(trackGain = -6f, albumGain = -7f, trackPeak = 0.9f)
        val song = song("s", "al").copy(coverArtId = "cv", durationSeconds = 200, replayGain = gain, starredAt = 1)
        val item = song.toMediaItem()

        assertThat(item.albumKey).isEqualTo("al")
        assertThat(item.replayGain).isEqualTo(gain)
        assertThat(item.isStarred).isTrue()
        assertThat(item.withStarred(false).isStarred).isFalse()
        assertThat(item.localConfiguration!!.uri.toString()).isEqualTo("segue://song/s")
        assertThat(item.mediaMetadata.artworkUri.toString()).isEqualTo("segue://cover/cv?size=512")
        val queueItem = item.toQueueItem(3)
        assertThat(queueItem.timelineIndex).isEqualTo(3)
        assertThat(queueItem.durationMillis).isEqualTo(200_000)
        assertThat(queueItem.coverArtId).isEqualTo("cv")

        val stripped = item.buildUpon().setUri(null as String?).build()
        assertThat(stripped.withPlayableUri().localConfiguration!!.uri.toString()).isEqualTo("segue://song/s")
    }
}
