package com.quantumff.segue.feature.nowplaying

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.PlayingSong
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class RelatedUiStateTest {
    private val loaded = RelatedUiState.Loaded(persistentListOf(TestData.song("s1"), TestData.song("s2")))

    @Test
    fun relatedLeavesOutTheSongThatIsPlaying() {
        val shown = loaded.shownWhile(PlayingSong("s2", isPlaying = true)) as RelatedUiState.Loaded

        assertThat(shown.songs.map { it.id }).containsExactly("s1")
    }

    @Test
    fun aPausedCurrentSongIsLeftOutToo() {
        val shown = loaded.shownWhile(PlayingSong("s1", isPlaying = false)) as RelatedUiState.Loaded

        assertThat(shown.songs.map { it.id }).containsExactly("s2")
    }

    @Test
    fun relatedRowsAreMarkedLikeOtherSongLists() {
        val playing = PlayingSong("s9", isPlaying = true)

        val shown = loaded.shownWhile(playing) as RelatedUiState.Loaded

        assertThat(shown.playing).isEqualTo(playing)
        assertThat(shown.songs.map { it.id }).containsExactly("s1", "s2").inOrder()
    }

    @Test
    fun withNothingPlayingEverySongShows() {
        assertThat(loaded.shownWhile(null)).isEqualTo(loaded)
    }

    @Test
    fun onlyLoadedSongsAreFiltered() {
        val playing = PlayingSong("s1", isPlaying = true)

        assertThat(RelatedUiState.Loading.shownWhile(playing)).isEqualTo(RelatedUiState.Loading)
        assertThat(RelatedUiState.Error.shownWhile(playing)).isEqualTo(RelatedUiState.Error)
        assertThat(RelatedUiState.Unavailable.shownWhile(playing)).isEqualTo(RelatedUiState.Unavailable)
    }
}
