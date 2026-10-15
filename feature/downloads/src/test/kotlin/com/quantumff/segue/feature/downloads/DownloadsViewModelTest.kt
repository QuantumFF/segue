package com.quantumff.segue.feature.downloads

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PinnedDownload
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DownloadsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val downloads = FakeDownloadRepository()
    private val offline = FakeOfflineModeRepository()

    private fun pin(id: String, songs: Int, completed: Int, failed: Int = 0, paused: Int = 0) =
        PinnedDownload(PinKind.Album, id, id, null, songs, completed, failed, paused, completed / songs.toFloat(), 0)

    @Test
    fun pinsAreSortedIntoActiveFailedAndDownloaded() = runTest {
        downloads.overview.value = DownloadOverview(
            pins = persistentListOf(
                pin("zeta", songs = 2, completed = 2),
                pin("busy", songs = 4, completed = 1),
                pin("held", songs = 2, completed = 0, paused = 2),
                pin("broken", songs = 2, completed = 1, failed = 1),
                pin("alpha", songs = 1, completed = 1),
            ),
            usedBytes = 100,
            freeBytes = 300,
        )
        val viewModel = DownloadsViewModel(downloads, offline)

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.active.map { it.id }).containsExactly("busy", "held").inOrder()
            assertThat(state.failed.map { it.id }).containsExactly("broken")
            assertThat(state.downloaded.map { it.id }).containsExactly("alpha", "zeta").inOrder()
            assertThat(state.storageFraction).isEqualTo(0.25f)
            assertThat(state.isEmpty).isFalse()
        }
    }

    @Test
    fun actionsReachTheRepository() = runTest {
        val viewModel = DownloadsViewModel(downloads, offline)
        val pin = pin("a", songs = 2, completed = 1)

        viewModel.pause(pin)
        viewModel.resume(pin)
        viewModel.setForceOffline(true)
        viewModel.removeAll()

        assertThat(downloads.paused).containsExactly(PinKind.Album to "a")
        assertThat(downloads.resumed).containsExactly(PinKind.Album to "a")
        assertThat(downloads.preferences.value.forceOffline).isTrue()
        assertThat(downloads.removedAll).isTrue()
    }

    @Test
    fun emptyOverviewIsEmptyOnceLoaded() = runTest {
        offline.isOffline.value = true
        DownloadsViewModel(downloads, offline).uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.isEmpty).isTrue()
            assertThat(state.isOffline).isTrue()
        }
    }
}
