package com.quantumff.segue.feature.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.playback.nowplaying.NowPlayingSnapshot
import org.junit.Test

class NowPlayingPreferencesTest {
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
        NowPlayingPreferences.write(prefs, nude)

        assertThat(NowPlayingPreferences.read(prefs)).isEqualTo(nude)
    }

    @Test
    fun `writing an empty snapshot clears the track`() {
        val prefs = mutablePreferencesOf()
        NowPlayingPreferences.write(prefs, nude)
        NowPlayingPreferences.write(prefs, NowPlayingSnapshot.Empty)

        assertThat(NowPlayingPreferences.read(prefs)).isEqualTo(NowPlayingSnapshot.Empty)
    }

    @Test
    fun `nothing stored reads as empty`() {
        assertThat(NowPlayingPreferences.read(mutablePreferencesOf())).isEqualTo(NowPlayingSnapshot.Empty)
    }

    @Test
    fun `an unknown shuffle mode reads as off`() {
        val prefs = mutablePreferencesOf()
        NowPlayingPreferences.write(prefs, nude)
        prefs[NowPlayingPreferences.SHUFFLE] = "Sideways"

        assertThat(NowPlayingPreferences.read(prefs).shuffleMode).isEqualTo(ShuffleMode.Off)
    }
}
