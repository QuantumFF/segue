package com.quantumff.segue.core.playback.nowplaying

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.testing.data.TestData
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NowPlayingSnapshotTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()

    @After
    fun tearDown() = exo.release()

    @Test
    fun `nothing queued is the empty snapshot`() {
        assertThat(NowPlayingSnapshot.of(exo)).isEqualTo(NowPlayingSnapshot.Empty)
    }

    @Test
    fun `the current song, and whether the user means it to play`() {
        exo.setMediaItems(listOf("a", "b").map { TestData.song(it, albumId = "al").toMediaItem() }, 1, 0)

        val paused = NowPlayingSnapshot(
            songId = "b",
            title = "Song b",
            artist = "Artist 1",
            coverArtId = "al-al",
            isPlaying = false,
        )
        assertThat(NowPlayingSnapshot.of(exo)).isEqualTo(paused)

        exo.prepare()
        exo.play()
        // Still buffering: the play button follows the intent, not the buffer.
        assertThat(NowPlayingSnapshot.of(exo)).isEqualTo(paused.copy(isPlaying = true))
    }
}
