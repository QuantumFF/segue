package com.quantumff.segue.core.playback.nowplaying

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ShuffleMode
import org.junit.Test

class NowPlayingPublisherTest {
    private val received = mutableListOf<NowPlayingSnapshot>()
    private val publisher = NowPlayingPublisher(setOf(NowPlayingListener { received += it }))

    private val nude = NowPlayingSnapshot(
        songId = "so-1",
        title = "Nude",
        artist = "Radiohead",
        coverArtId = "al-1",
        isPlaying = true,
        shuffleMode = ShuffleMode.Off,
    )

    @Test
    fun `listeners hear each change once`() {
        publisher.publish(nude)
        publisher.publish(nude)
        publisher.publish(nude.copy(isPlaying = false))

        assertThat(received).containsExactly(nude, nude.copy(isPlaying = false)).inOrder()
    }

    @Test
    fun `the empty snapshot has nothing playing`() {
        assertThat(NowPlayingSnapshot.Empty.hasTrack).isFalse()
        assertThat(nude.hasTrack).isTrue()
    }
}
