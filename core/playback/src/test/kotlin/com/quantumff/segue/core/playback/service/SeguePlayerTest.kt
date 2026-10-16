package com.quantumff.segue.core.playback.service

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.media.albumKey
import com.quantumff.segue.core.playback.media.isStarred
import com.quantumff.segue.core.playback.media.replayGain
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.media.toQueueItem
import com.quantumff.segue.core.playback.media.withPlayableUri
import com.quantumff.segue.core.playback.media.withStarred
import com.quantumff.segue.core.playback.sync.QueueState
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeguePlayerTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private val player = SeguePlayer(exo)

    @After
    fun tearDown() = exo.release()

    private fun song(id: String, album: String?) =
        Song(id = id, title = id, albumId = album, album = album, artist = null, artistId = null)

    private val queue = listOf(song("a1", "a"), song("a2", "a"), song("b1", "b"), song("c1", "c"))

    @Test
    fun `shuffle is never offered and a stray shuffle request is ignored`() {
        player.setMediaItems(queue.map { it.toMediaItem() })

        assertThat(player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)).isFalse()
        assertThat(player.availableCommands.contains(Player.COMMAND_SET_SHUFFLE_MODE)).isFalse()
        player.shuffleModeEnabled = true
        assertThat(exo.shuffleModeEnabled).isFalse()
        assertThat(player.shuffleModeEnabled).isFalse()
    }

    @Test
    fun `the queue state is the queue in order with the current item and position`() {
        player.setMediaItems(queue.map { it.toMediaItem() }, 2, 1_500)

        assertThat(player.queueState()).isEqualTo(QueueState(listOf("a1", "a2", "b1", "c1"), 2, 1_500))
        assertThat(player.upNext(5)).containsExactly("c1")
    }

    @Test
    fun `restoring a saved queue resumes exactly`() {
        val state = QueueState(listOf("a1", "a2", "b1", "c1"), 1, 4_000)

        player.restore(queue.map { it.toMediaItem() }, state)

        assertThat(player.queueState()).isEqualTo(state)
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
