package com.quantumff.segue.core.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.SegueSessionCommands
import com.quantumff.segue.core.testing.data.TestData
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerUiStateProjectionTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private val projection = PlayerUiStateProjection()

    @After
    fun tearDown() = exo.release()

    private fun items(vararg ids: String) = ids.map { TestData.song(it).toMediaItem() }

    private fun project(extras: Bundle = Bundle.EMPTY) = projection.project(exo, extras)

    @Test
    fun `an empty player is the empty state`() {
        assertThat(project()).isEqualTo(PlayerUiState())
    }

    @Test
    fun `the queue in play order, the current item and its position`() {
        exo.setMediaItems(items("a", "b", "c"), 1, 5_000)

        val state = project()

        assertThat(state.queue.map { it.songId }).containsExactly("a", "b", "c").inOrder()
        assertThat(state.queue.map { it.timelineIndex }).containsExactly(0, 1, 2).inOrder()
        assertThat(state.currentQueueIndex).isEqualTo(1)
        assertThat(state.currentItem?.songId).isEqualTo("b")
        assertThat(state.positionMillis).isEqualTo(5_000)
        assertThat(state.isPlaying).isFalse()
    }

    @Test
    fun `before the player knows the duration, the item's own stands in`() {
        exo.setMediaItems(items("a"))

        assertThat(project().durationMillis).isEqualTo(200_000)
    }

    @Test
    fun `the queue is rebuilt only when the timeline changes`() {
        exo.setMediaItems(items("a", "b"))
        val first = project().queue

        exo.seekTo(1, 3_000)
        assertThat(project().queue).isSameInstanceAs(first)

        exo.addMediaItems(items("c"))
        val grown = project().queue
        assertThat(grown).isNotSameInstanceAs(first)
        assertThat(grown.map { it.songId }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `buffering after play`() {
        exo.setMediaItems(items("a"))
        exo.prepare()
        exo.play()

        assertThat(project().isBuffering).isTrue()
    }

    @Test
    fun `repeat modes map both ways`() {
        for (mode in RepeatMode.entries) {
            exo.repeatMode = mode.playerRepeatMode
            assertThat(project().repeatMode).isEqualTo(mode)
        }
    }

    @Test
    fun `the sleep timer comes from the session extras`() {
        assertThat(project(SegueSessionCommands.sleepArgs(SleepTimer.EndOfAlbum)).sleepTimer)
            .isEqualTo(SleepTimer.EndOfAlbum)
        assertThat(project(SegueSessionCommands.sleepArgs(SleepTimer.AtTime(9_000))).sleepTimer)
            .isEqualTo(SleepTimer.AtTime(9_000))
        assertThat(project().sleepTimer).isNull()
    }

    @Test
    fun `the queue source comes from the session extras`() {
        val extras = SegueSessionCommands.sessionExtras(SleepTimer.EndOfTrack, QueueSource.Playlist("p1", "Road trip"))

        val state = project(extras)

        assertThat(state.queueSource).isEqualTo(QueueSource.Playlist("p1", "Road trip"))
        assertThat(state.sleepTimer).isEqualTo(SleepTimer.EndOfTrack)
        assertThat(project().queueSource).isEqualTo(QueueSource.Unknown)
    }
}
