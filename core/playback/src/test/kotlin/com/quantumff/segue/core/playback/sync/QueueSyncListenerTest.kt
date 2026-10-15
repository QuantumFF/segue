package com.quantumff.segue.core.playback.sync

import android.content.Context
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.SeguePlayer
import kotlin.random.Random
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The upload triggers of docs/PLAN.md against a real ExoPlayer. */
@RunWith(RobolectricTestRunner::class)
class QueueSyncListenerTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private val sink = RecordingSink()
    private lateinit var listener: QueueSyncListener
    private val player = SeguePlayer(exo, Random(3)) { listener.onShuffleModeChanged() }

    private val songs = listOf("a1" to "a", "a2" to "a", "b1" to "b", "b2" to "b").map { (id, album) ->
        Song(id = id, title = id, albumId = album, album = album, artist = null, artistId = null).toMediaItem()
    }

    @Before
    fun setUp() {
        listener = QueueSyncListener(player, sink)
        exo.addListener(listener)
        player.setMediaItems(songs, 0, 0)
        idle()
        sink.events.clear()
    }

    @After
    fun tearDown() = exo.release()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `a seek within the track is a seek`() {
        player.seekTo(30_000)
        idle()
        assertThat(sink.events.map { it.first }).contains(QueueEvent.Seek)
        assertThat(sink.events.last().second.positionMillis).isEqualTo(30_000)
    }

    @Test
    fun `skipping to another track is a track change`() {
        player.seekTo(2, 0)
        idle()
        assertThat(sink.events.map { it.first }).contains(QueueEvent.TrackTransition)
        assertThat(sink.events.last().second.currentIndex).isEqualTo(2)
    }

    @Test
    fun `pausing is a pause and playing retires a remote offer instead`() {
        player.play()
        idle()
        assertThat(sink.playbackStarts).isEqualTo(1)
        assertThat(sink.events).isEmpty()

        player.pause()
        idle()
        assertThat(sink.events.map { it.first }).containsExactly(QueueEvent.Pause)
    }

    @Test
    fun `adding, moving and removing items are queue edits`() {
        player.addMediaItems(listOf(songs[0]))
        idle()
        player.moveMediaItem(0, 3)
        idle()
        player.removeMediaItem(1)
        idle()
        assertThat(sink.events.map { it.first }).containsExactly(QueueEvent.QueueEdit, QueueEvent.QueueEdit, QueueEvent.QueueEdit)
    }

    @Test
    fun `every shuffle mode change is reported, including tracks to albums`() {
        player.setShuffleMode(ShuffleMode.Tracks)
        idle()
        sink.events.clear()

        player.setShuffleMode(ShuffleMode.Albums)
        idle()

        assertThat(sink.events).isNotEmpty()
        assertThat(sink.events.last().second.shuffleMode).isEqualTo(ShuffleMode.Albums)
    }

    @Test
    fun `clearing the queue clears the saved queue`() {
        player.clearMediaItems()
        idle()
        assertThat(sink.clears).isEqualTo(1)
        assertThat(sink.events).isEmpty()
    }

    @Test
    fun `a player that only waits raises nothing`() {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(10))
        assertThat(sink.events).isEmpty()
    }

    private class RecordingSink : QueueSyncSink {
        val events = mutableListOf<Pair<QueueEvent, QueueState>>()
        var clears = 0
        var playbackStarts = 0

        override fun onQueueEvent(event: QueueEvent, state: QueueState) {
            events += event to state
        }

        override fun onQueueCleared() {
            clears++
        }

        override fun onLocalPlaybackStarted() {
            playbackStarts++
        }
    }
}
