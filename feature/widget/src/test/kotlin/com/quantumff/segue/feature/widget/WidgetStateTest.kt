package com.quantumff.segue.feature.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.playback.nowplaying.NowPlayingSnapshot
import org.junit.Test

class WidgetStateTest {
    private val nude = NowPlayingSnapshot(
        songId = "so-1",
        title = "Nude",
        artist = "Radiohead",
        coverArtId = "al-1",
        isPlaying = true,
        shuffleMode = ShuffleMode.Albums,
    )

    @Test
    fun `a snapshot survives the preferences round trip`() {
        val prefs = mutablePreferencesOf()
        WidgetState.write(prefs, nude)

        assertThat(WidgetState.read(prefs)).isEqualTo(nude)
    }

    @Test
    fun `writing an empty snapshot clears the track`() {
        val prefs = mutablePreferencesOf()
        WidgetState.write(prefs, nude)
        WidgetState.write(prefs, NowPlayingSnapshot.Empty)

        assertThat(WidgetState.read(prefs)).isEqualTo(NowPlayingSnapshot.Empty)
    }

    @Test
    fun `nothing stored reads as empty`() {
        assertThat(WidgetState.read(mutablePreferencesOf())).isEqualTo(NowPlayingSnapshot.Empty)
    }

    @Test
    fun `an unknown shuffle mode reads as off`() {
        val prefs = mutablePreferencesOf()
        WidgetState.write(prefs, nude)
        prefs[WidgetState.SHUFFLE] = "Sideways"

        assertThat(WidgetState.read(prefs).shuffleMode).isEqualTo(ShuffleMode.Off)
    }

    @Test
    fun `the next shuffle mode cycles off, tracks, albums`() {
        assertThat(nextShuffleMode(ShuffleMode.Off)).isEqualTo(ShuffleMode.Tracks)
        assertThat(nextShuffleMode(ShuffleMode.Tracks)).isEqualTo(ShuffleMode.Albums)
        assertThat(nextShuffleMode(ShuffleMode.Albums)).isEqualTo(ShuffleMode.Off)
    }
}
