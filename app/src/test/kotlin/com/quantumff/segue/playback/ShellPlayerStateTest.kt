package com.quantumff.segue.playback

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueueItem
import com.quantumff.segue.core.model.RemoteQueueOffer
import com.quantumff.segue.core.model.RepeatMode
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class ShellPlayerStateTest {
    private fun item(n: Int, timelineIndex: Int = n) = QueueItem(
        songId = "s$n",
        timelineIndex = timelineIndex,
        title = "Song $n",
        artist = "Artist $n",
        album = "Album",
        albumId = "a",
        coverArtId = "c$n",
        durationMillis = 1_000,
        isStarred = false,
    )

    private fun state(count: Int, current: Int, repeat: RepeatMode = RepeatMode.Off): PlayerUiState {
        // Play order differs from timeline order, as it does with shuffle on.
        val queue = (0 until count).map { item(it, timelineIndex = count - 1 - it) }.toImmutableList()
        return PlayerUiState(
            currentItem = queue.getOrNull(current),
            currentQueueIndex = current,
            isPlaying = true,
            queue = queue,
            repeatMode = repeat,
        )
    }

    @Test
    fun `an empty queue has no track`() {
        assertThat(PlayerUiState().toShellPlayerState().current).isNull()
    }

    @Test
    fun `a remote queue offer reaches the shell, with or without a local queue`() {
        val offer = RemoteQueueOffer("Feishin", trackCount = 3, currentTitle = "Nude")

        assertThat(PlayerUiState(remoteQueue = offer).toShellPlayerState().remoteOffer).isEqualTo(offer)
        assertThat(state(3, 1).copy(remoteQueue = offer).toShellPlayerState().remoteOffer).isEqualTo(offer)
    }

    @Test
    fun `the current track maps to the mini player`() {
        val current = state(count = 3, current = 1).toShellPlayerState().current

        assertThat(current?.title).isEqualTo("Song 1")
        assertThat(current?.artist).isEqualTo("Artist 1")
        assertThat(current?.coverArtId).isEqualTo("c1")
        assertThat(current?.isPlaying).isTrue()
    }

    @Test
    fun `adjacent tracks follow play order and carry their timeline index`() {
        val player = state(count = 3, current = 1).toShellPlayerState()

        assertThat(player.currentTimelineIndex).isEqualTo(1)
        assertThat(player.previous?.track?.title).isEqualTo("Song 0")
        assertThat(player.previous?.timelineIndex).isEqualTo(2)
        assertThat(player.next?.track?.title).isEqualTo("Song 2")
        assertThat(player.next?.timelineIndex).isEqualTo(0)
    }

    @Test
    fun `the ends of the queue have no neighbor without repeat`() {
        assertThat(state(count = 3, current = 0).toShellPlayerState().previous).isNull()
        assertThat(state(count = 3, current = 2).toShellPlayerState().next).isNull()
    }

    @Test
    fun `repeat all wraps around the ends`() {
        assertThat(state(count = 3, current = 0, RepeatMode.All).toShellPlayerState().previous?.track?.title)
            .isEqualTo("Song 2")
        assertThat(state(count = 3, current = 2, RepeatMode.All).toShellPlayerState().next?.track?.title)
            .isEqualTo("Song 0")
    }

    @Test
    fun `a single track has no neighbor even with repeat all`() {
        val player = state(count = 1, current = 0, RepeatMode.All).toShellPlayerState()

        assertThat(player.previous).isNull()
        assertThat(player.next).isNull()
    }

    @Test
    fun `progress is position over duration, clamped`() {
        assertThat(progressOf(positionMillis = 500, durationMillis = 2_000)).isEqualTo(0.25f)
        assertThat(progressOf(positionMillis = 3_000, durationMillis = 2_000)).isEqualTo(1f)
        assertThat(progressOf(positionMillis = 100, durationMillis = 0)).isEqualTo(0f)
    }
}
