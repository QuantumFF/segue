package com.quantumff.segue.feature.nowplaying

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.component.DownloadBadgeState
import com.quantumff.segue.core.model.CurrentSong
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.SongAvailability
import com.quantumff.segue.core.model.SongDownload
import com.quantumff.segue.core.testing.data.TestData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RelatedUiStateTest {
    private val loaded = RelatedUiState.Loaded(persistentListOf(TestData.song("s1"), TestData.song("s2")))

    @Test
    fun relatedLeavesOutTheSongThatIsPlaying() {
        val shown = loaded.shownWhile(CurrentSong("s2", isPlaying = true)) as RelatedUiState.Loaded

        assertThat(shown.songs.map { it.id }).containsExactly("s1")
    }

    @Test
    fun aPausedCurrentSongIsLeftOutToo() {
        val shown = loaded.shownWhile(CurrentSong("s1", isPlaying = false)) as RelatedUiState.Loaded

        assertThat(shown.songs.map { it.id }).containsExactly("s2")
    }

    @Test
    fun relatedRowsAreMarkedLikeOtherSongLists() {
        val playing = CurrentSong("s9", isPlaying = true)

        val shown = loaded.shownWhile(playing) as RelatedUiState.Loaded

        assertThat(shown.currentSong).isEqualTo(playing)
        assertThat(shown.songs.map { it.id }).containsExactly("s1", "s2").inOrder()
    }

    @Test
    fun withNothingPlayingEverySongShows() {
        assertThat(loaded.shownWhile(null)).isEqualTo(loaded)
    }

    @Test
    fun onlyLoadedSongsAreFiltered() {
        val playing = CurrentSong("s1", isPlaying = true)

        assertThat(RelatedUiState.Loading.shownWhile(playing)).isEqualTo(RelatedUiState.Loading)
        assertThat(RelatedUiState.Error.shownWhile(playing)).isEqualTo(RelatedUiState.Error)
        assertThat(RelatedUiState.Unavailable.shownWhile(playing)).isEqualTo(RelatedUiState.Unavailable)
    }

    @Test
    fun offlineTheOfflineStateShowsAtOnceWithoutAskingTheServer() = runTest {
        val asked = mutableListOf<String>()

        val states = relatedPanel("ar1", isOffline = true) {
            asked += it
            listOf(TestData.song("s1"))
        }.toList()

        assertThat(states).containsExactly(RelatedUiState.Offline)
        assertThat(asked).isEmpty()
    }

    @Test
    fun onlineTheServerIsAskedForTheArtistsSimilarSongs() = runTest {
        val asked = mutableListOf<String>()
        val song = TestData.song("s1")

        val states = relatedPanel("ar1", isOffline = false) {
            asked += it
            listOf(song)
        }.toList()

        assertThat(states).containsExactly(RelatedUiState.Loading, RelatedUiState.Loaded(persistentListOf(song)))
            .inOrder()
        assertThat(asked).containsExactly("ar1")
    }

    @Test
    fun aFailedLoadOnlineIsAnErrorWithRetry() = runTest {
        val states = relatedPanel("ar1", isOffline = false) { null }.toList()

        assertThat(states.last()).isEqualTo(RelatedUiState.Error)
    }

    @Test
    fun relatedRowsThatCannotPlayAreDimmedAndShowDownloadBadges() {
        val availability = SongAvailability(
            isOffline = true,
            downloads = persistentMapOf(
                "s1" to SongDownload("s1", DownloadState.Completed, progress = 1f),
                "s2" to SongDownload("s2", DownloadState.Downloading, progress = 0.5f),
            ),
        )

        val rows = (loaded.shownWhile(current = null, availability = availability) as RelatedUiState.Loaded).trackRows()

        assertThat(rows.map { it.isAvailable }).containsExactly(true, false).inOrder()
        assertThat(rows.map { it.download })
            .containsExactly(DownloadBadgeState.Done, DownloadBadgeState.InProgress(0.5f))
            .inOrder()
    }

    @Test
    fun onlineEveryRelatedRowPlays() {
        val rows = (loaded.shownWhile(current = null, availability = SongAvailability()) as RelatedUiState.Loaded)
            .trackRows()

        assertThat(rows.map { it.isAvailable }).containsExactly(true, true)
        assertThat(rows.map { it.download }).containsExactly(null, null)
    }

    @Test
    fun relatedRowsKeepTheirPositionInTheirKey() {
        val twice = RelatedUiState.Loaded(persistentListOf(TestData.song("s1"), TestData.song("s1")))

        assertThat(twice.trackRows().map { it.id }).containsExactly("0:s1", "1:s1").inOrder()
    }

    @Test
    fun theOfflineStateIsNotFiltered() {
        val playing = CurrentSong("s1", isPlaying = true)

        assertThat(RelatedUiState.Offline.shownWhile(playing)).isEqualTo(RelatedUiState.Offline)
    }
}
