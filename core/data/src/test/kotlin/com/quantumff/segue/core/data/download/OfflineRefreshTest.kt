package com.quantumff.segue.core.data.download

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.data.snackbar.offline
import com.quantumff.segue.core.testing.repository.FakeArtistRepository
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import org.junit.Test

class OfflineRefreshTest {
    private val offlineMode = FakeOfflineModeRepository()
    private val view = OfflineLibraryView(
        offlineMode,
        FakeDownloadRepository(),
        FakeArtistRepository(),
        FakeLibraryRepository(),
        FakePlaylistRepository(),
    )
    private val snackbars = Snackbars()
    private var refreshes = 0

    @Test
    fun aRequestedRefreshOnlineAsksTheServerQuietly() {
        view.requestRefresh(snackbars) { refreshes++ }

        assertThat(refreshes).isEqualTo(1)
        assertThat(snackbars.current.value).isNull()
    }

    @Test
    fun aRequestedRefreshOfflineSaysSoInsteadOfAskingTheServer() {
        offlineMode.isOffline.value = true

        view.requestRefresh(snackbars) { refreshes++ }

        assertThat(refreshes).isEqualTo(0)
        assertThat(snackbars.current.value?.text).isEqualTo(SnackbarText.offline)
    }

    @Test
    fun theRefreshOnOpeningRunsOnlyOnline() {
        view.refreshIfOnline { refreshes++ }
        offlineMode.isOffline.value = true
        view.refreshIfOnline { refreshes++ }

        assertThat(refreshes).isEqualTo(1)
        assertThat(snackbars.current.value).isNull()
    }
}
