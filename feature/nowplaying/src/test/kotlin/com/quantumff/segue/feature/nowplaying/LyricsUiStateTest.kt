package com.quantumff.segue.feature.nowplaying

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.LyricLine
import com.quantumff.segue.core.model.Lyrics
import com.quantumff.segue.feature.nowplaying.lyrics.LyricsContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class LyricsUiStateTest {
    private val stored = persistentListOf(
        Lyrics(
            language = "en",
            synced = false,
            offsetMillis = 0,
            displayArtist = null,
            displayTitle = null,
            lines = persistentListOf(LyricLine(null, "First line"), LyricLine(null, "Second line")),
        ),
    )

    @Test
    fun offlineWithNothingStoredExplainsWhyInsteadOfOfferingRetry() {
        assertThat(lyricsUiState(lookup = null, isOffline = true)).isEqualTo(LyricsUiState.Offline)
    }

    @Test
    fun aFailedLookupOnlineIsAnErrorWithRetry() {
        assertThat(lyricsUiState(lookup = null, isOffline = false)).isEqualTo(LyricsUiState.Error)
    }

    @Test
    fun aDownloadedSongsStoredLyricsShowOffline() {
        val state = lyricsUiState(lookup = stored, isOffline = true)

        assertThat(state).isEqualTo(
            LyricsUiState.Loaded(LyricsContent.Unsynced(persistentListOf("First line", "Second line"))),
        )
    }

    @Test
    fun noLyricsOnTheServerReadsNoLyricsOnline() {
        assertThat(lyricsUiState(lookup = persistentListOf(), isOffline = false)).isEqualTo(LyricsUiState.Loaded(null))
    }

    @Test
    fun offlineALookupWithNothingShowsTheOfflineState() {
        assertThat(lyricsUiState(lookup = persistentListOf(), isOffline = true)).isEqualTo(LyricsUiState.Offline)
    }
}
