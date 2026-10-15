package com.quantumff.segue.feature.playlist

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AddToPlaylistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playlists = FakePlaylistRepository().apply {
        playlistsState.value = listOf(TestData.playlist("mine"), TestData.playlist("shared", readonly = true))
    }

    private fun TestScope.viewModel(): AddToPlaylistViewModel {
        val viewModel = AddToPlaylistViewModel(playlists)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun listsOnlyEditablePlaylists() = runTest {
        assertThat(viewModel().uiState.value.playlists.map { it.id }).containsExactly("mine")
    }

    @Test
    fun addToExistingPlaylistAddsSongsAndFinishes() = runTest {
        val viewModel = viewModel()

        viewModel.addTo("mine", listOf("s1", "s2"))

        assertThat(playlists.added).containsExactly("mine" to listOf("s1", "s2"))
        assertThat(viewModel.uiState.value.isDone).isTrue()
    }

    @Test
    fun createNewPlaylistWithSongs() = runTest {
        val viewModel = viewModel()

        viewModel.createWith("Fresh", listOf("s1", "s2"))

        val created = playlists.playlistsState.value.single { it.name == "Fresh" }
        assertThat(created.songCount).isEqualTo(2)
        assertThat(viewModel.uiState.value.isDone).isTrue()
    }

    @Test
    fun failureShowsErrorAndResetClearsIt() = runTest {
        val viewModel = viewModel()
        playlists.error = RemoteException.Unreachable(IOException("offline"))

        viewModel.addTo("mine", listOf("s1"))
        assertThat(viewModel.uiState.value.failed).isTrue()
        assertThat(viewModel.uiState.value.isDone).isFalse()

        viewModel.reset()
        assertThat(viewModel.uiState.value.failed).isFalse()
    }
}
