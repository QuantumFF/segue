package com.quantumff.segue.feature.playlist

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PlaylistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playlists = FakePlaylistRepository().apply {
        playlistsState.value = listOf(TestData.playlist("p1", songCount = 3))
        songsByPlaylist.value = mapOf("p1" to listOf(TestData.song("a"), TestData.song("b"), TestData.song("c")))
    }
    private val stars = FakeStarRatingRepository()

    private fun TestScope.viewModel(): PlaylistViewModel {
        val viewModel = PlaylistViewModel(SavedStateHandle(mapOf("playlistId" to "p1")), playlists, stars)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val PlaylistViewModel.songIds get() = uiState.value.entries.map { it.song.id }

    @Test
    fun rendersRoomRowsAndRefreshesOnEntry() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.songIds).containsExactly("a", "b", "c").inOrder()
        assertThat(viewModel.uiState.value.loadState).isEqualTo(LoadState.Loaded)
        assertThat(playlists.refreshed).containsExactly("p1")
    }

    @Test
    fun duplicateSongsGetDistinctKeys() = runTest {
        playlists.songsByPlaylist.value = mapOf("p1" to listOf(TestData.song("a"), TestData.song("a")))

        val keys = viewModel().uiState.value.entries.map { it.key }

        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun moveAndRemoveThenSaveSendsWorkingOrder() = runTest {
        val viewModel = viewModel()

        viewModel.startEditing()
        viewModel.move(from = 2, to = 0)
        viewModel.removeEntry(viewModel.uiState.value.entries[2].key)
        assertThat(viewModel.songIds).containsExactly("c", "a").inOrder()

        viewModel.save()

        assertThat(playlists.replaced).containsExactly("p1" to listOf("c", "a"))
        assertThat(viewModel.uiState.value.isEditing).isFalse()
        assertThat(viewModel.songIds).containsExactly("c", "a").inOrder()
    }

    @Test
    fun cancelRestoresSavedOrder() = runTest {
        val viewModel = viewModel()

        viewModel.startEditing()
        viewModel.move(from = 0, to = 2)
        viewModel.cancelEditing()

        assertThat(viewModel.uiState.value.isEditing).isFalse()
        assertThat(viewModel.songIds).containsExactly("a", "b", "c").inOrder()
        assertThat(playlists.replaced).isEmpty()
    }

    @Test
    fun saveFailureKeepsEditModeAndEdits() = runTest {
        val viewModel = viewModel()
        viewModel.startEditing()
        viewModel.move(from = 0, to = 1)
        playlists.error = RemoteException.Unreachable(IOException("offline"))

        viewModel.save()

        val state = viewModel.uiState.value
        assertThat(state.isEditing).isTrue()
        assertThat(state.isSaving).isFalse()
        assertThat(state.message).isEqualTo(PlaylistMessage.SaveFailed)
        assertThat(viewModel.songIds).containsExactly("b", "a", "c").inOrder()
    }

    @Test
    fun readonlyPlaylistCannotBeEdited() = runTest {
        playlists.playlistsState.value = listOf(TestData.playlist("p1", readonly = true))
        val viewModel = viewModel()

        viewModel.startEditing()

        assertThat(viewModel.uiState.value.isEditable).isFalse()
        assertThat(viewModel.uiState.value.isEditing).isFalse()
    }

    @Test
    fun deleteMarksDeleted() = runTest {
        val viewModel = viewModel()

        viewModel.delete()

        assertThat(playlists.deleted).containsExactly("p1")
        assertThat(viewModel.uiState.value.isDeleted).isTrue()
    }

    @Test
    fun removeFromMenuRemovesThatPosition() = runTest {
        val viewModel = viewModel()

        viewModel.removeAt(1)

        assertThat(viewModel.songIds).containsExactly("a", "c").inOrder()
    }

    @Test
    fun refreshFailureWithCachedDataShowsOfflineState() = runTest {
        playlists.error = RemoteException.Unreachable(IOException("offline"))

        val state = viewModel().uiState.value

        assertThat(state.loadState).isEqualTo(LoadState.Failed)
        assertThat(state.playlist).isNotNull()
        assertThat(state.entries).hasSize(3)
    }

    @Test
    fun toggleStarFlipsSongStar() = runTest {
        val viewModel = viewModel()

        viewModel.toggleStar(TestData.song("a"))

        assertThat(stars.starred[MediaKind.Song to "a"]).isTrue()
    }
}
