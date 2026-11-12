package com.quantumff.segue.feature.nowplaying

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.repository.LyricsRepository
import com.quantumff.segue.core.data.repository.PlayQueueRemoteRepository
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.data.repository.SongFileInfoRepository
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.Lyrics
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.model.SongFileInfo
import com.quantumff.segue.core.playback.PlaybackController
import com.quantumff.segue.core.playback.sync.QueueSyncEngine
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.play.FakePlayerStateSource
import com.quantumff.segue.core.testing.play.RecordingPlayIntents
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeUserPreferencesRepository
import com.quantumff.segue.core.testing.repository.offlineLibraryView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Now Playing's speed: the ⋮ menu's Speed sheet stores the chosen preset, and the playback service
 * applies it. The real [PlaybackController] is never connected: these tests send it no commands.
 */
@RunWith(RobolectricTestRunner::class)
class NowPlayingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeUserPreferencesRepository()
    private val player = FakePlayerStateSource(TestData.playerOn("s1"))
    private val artists = FakeArtistRepository()
    private val library = FakeLibraryRepository()
    private val playlists = FakePlaylistRepository()

    private val viewModel by lazy {
        NowPlayingViewModel(
            player = player,
            playback = PlaybackController(
                ApplicationProvider.getApplicationContext<Context>(),
                QueueSyncEngine(NoRemoteQueue, NoSnapshot, preferences, TestScope()),
                artists,
                mainDispatcherRule.testDispatcher,
            ),
            library = library,
            lyricsRepository = NoLyrics,
            artists = artists,
            fileInfo = NoFileInfo,
            playIntents = RecordingPlayIntents(),
            playlists = playlists,
            snackbars = Snackbars(),
            offlineView = offlineLibraryView(artists = artists, library = library, playlists = playlists),
            preferences = preferences,
        )
    }

    @Test
    fun `at normal speed with no sleep timer, the more button has no badge`() = runTest {
        backgroundScope.launchCollecting(viewModel.uiState)

        assertThat(viewModel.uiState.value.speed).isEqualTo(1f)
        assertThat(viewModel.uiState.value.moreBadge).isFalse()
    }

    @Test
    fun `choosing a speed preset stores it and badges the more button`() = runTest {
        backgroundScope.launchCollecting(viewModel.uiState)

        viewModel.setPlaybackSpeed(1.5f)

        assertThat(preferences.preferences.value.playbackSpeed).isEqualTo(1.5f)
        assertThat(viewModel.uiState.value.speed).isEqualTo(1.5f)
        assertThat(viewModel.uiState.value.moreBadge).isTrue()
    }

    @Test
    fun `the speed shown is the one the player plays at`() = runTest {
        preferences.preferences.update { it.copy(playbackSpeed = 1.5f) }
        backgroundScope.launchCollecting(viewModel.uiState)

        // The service has not applied the stored speed (yet): Now Playing says what plays.
        assertThat(viewModel.uiState.value.speed).isEqualTo(1f)
        assertThat(viewModel.uiState.value.moreBadge).isFalse()

        player.state.update { it.copy(speed = 1.5f) }
        assertThat(viewModel.uiState.value.speed).isEqualTo(1.5f)
    }

    @Test
    fun `a chosen speed shows at once, then follows the player`() = runTest {
        backgroundScope.launchCollecting(viewModel.uiState)

        viewModel.setPlaybackSpeed(1.5f)
        assertThat(viewModel.uiState.value.speed).isEqualTo(1.5f)

        player.state.update { it.copy(speed = 1.5f) }
        assertThat(viewModel.uiState.value.speed).isEqualTo(1.5f)

        // Changed from elsewhere, such as a car's controls.
        player.state.update { it.copy(speed = 1.25f) }
        assertThat(viewModel.uiState.value.speed).isEqualTo(1.25f)
    }

    @Test
    fun `a chosen speed the player answers with another speed shows the player's`() = runTest {
        backgroundScope.launchCollecting(viewModel.uiState)

        viewModel.setPlaybackSpeed(2f)
        player.state.update { it.copy(speed = 1.75f) }

        assertThat(viewModel.uiState.value.speed).isEqualTo(1.75f)
    }

    @Test
    fun `a running sleep timer badges the more button at normal speed`() = runTest {
        player.state.value = TestData.playerOn("s1").copy(sleepTimer = SleepTimer.EndOfTrack)
        backgroundScope.launchCollecting(viewModel.uiState)

        assertThat(viewModel.uiState.value.moreBadge).isTrue()
    }

    private fun CoroutineScope.launchCollecting(flow: Flow<*>) = launch(UnconfinedTestDispatcher()) { flow.collect {} }

    private object NoLyrics : LyricsRepository {
        override suspend fun lyrics(songId: String): ImmutableList<Lyrics> = persistentListOf()
    }

    private object NoFileInfo : SongFileInfoRepository {
        override suspend fun fileInfo(songId: String) =
            SongFileInfo(path = null, samplingRate = null, bitDepth = null, channelCount = null)
    }

    private object NoRemoteQueue : PlayQueueRemoteRepository {
        override suspend fun fetch(): RemotePlayQueue? = null

        override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long) = Unit

        override suspend fun deviceClientName(): String = "Segue-test"
    }

    private object NoSnapshot : QueueSnapshotRepository {
        override val snapshot: Flow<QueueSnapshot?> = flowOf(null)

        override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long, source: QueueSource) =
            Unit

        override suspend fun seenRemoteChange(): Long? = null

        override suspend fun markRemoteChangeSeen(changedAt: Long) = Unit
    }
}
