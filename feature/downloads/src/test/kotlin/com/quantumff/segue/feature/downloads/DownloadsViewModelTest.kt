package com.quantumff.segue.feature.downloads

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.R as DataR
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.DownloadOverview
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PinnedDownload
import com.quantumff.segue.core.model.StorageLimit
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
    private val snackbars = Snackbars()

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
        val viewModel = DownloadsViewModel(downloads, offline, snackbars)

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
    fun aReachedStorageLimitIsReported() = runTest {
        downloads.overview.value = DownloadOverview(
            pins = persistentListOf(pin("busy", songs = 4, completed = 1)),
            usedBytes = 2_100_000_000L,
            freeBytes = 1_000,
            storageLimit = StorageLimit.Gb2,
            limitReached = true,
        )
        val viewModel = DownloadsViewModel(downloads, offline, snackbars)

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.storageLimit).isEqualTo(StorageLimit.Gb2)
            assertThat(state.limitReached).isTrue()
            // The bar fills against the limit, not the free space.
            assertThat(state.storageFraction).isEqualTo(1f)
        }
    }

    @Test
    fun actionsReachTheRepository() = runTest {
        val viewModel = DownloadsViewModel(downloads, offline, snackbars)
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
        DownloadsViewModel(downloads, offline, snackbars).uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.isEmpty).isTrue()
            assertThat(state.isOffline).isTrue()
        }
    }

    @Test
    fun cancellingThenUndoingPinsItAgainAndDeletesNoFiles() = runTest {
        val pin = pin("In Rainbows", songs = 10, completed = 4)
        downloads.pins.value = mapOf((PinKind.Album to "In Rainbows") to DownloadStatus.Queued)
        val viewModel = DownloadsViewModel(downloads, offline, snackbars)

        viewModel.remove(pin)

        assertThat(downloads.pins.value).isEmpty()
        val message = checkNotNull(snackbars.current.value)
        assertThat(message.text).isEqualTo(SnackbarText.of(DataR.string.core_data_download_removed, "In Rainbows"))

        snackbars.onAction(message)

        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Album to "In Rainbows")
        assertThat(downloads.deleted).isEmpty()
    }

    @Test
    fun removingDeletesTheFilesOnceTheSnackbarIsGone() = runTest {
        val pin = pin("In Rainbows", songs = 10, completed = 10)
        downloads.pins.value = mapOf((PinKind.Album to "In Rainbows") to DownloadStatus.Downloaded)
        val viewModel = DownloadsViewModel(downloads, offline, snackbars)

        viewModel.remove(pin)

        assertThat(downloads.deleted).isEmpty()

        snackbars.onDismiss(checkNotNull(snackbars.current.value))

        assertThat(downloads.pins.value).isEmpty()
        assertThat(downloads.deleted).containsExactly(PinKind.Album to "In Rainbows")
    }
}
