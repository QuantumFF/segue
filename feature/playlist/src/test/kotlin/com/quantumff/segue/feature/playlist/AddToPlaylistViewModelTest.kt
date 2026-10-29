package com.quantumff.segue.feature.playlist

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AddToPlaylistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mine = TestData.playlist("mine", name = "Road trip")
    private val playlists = FakePlaylistRepository().apply {
        playlistsState.value = listOf(mine, TestData.playlist("shared", readonly = true))
    }
    private val snackbars = Snackbars()

    private fun TestScope.removals() = PlaylistRemovals(playlists, snackbars, backgroundScope)

    private fun TestScope.viewModel(removals: PlaylistRemovals = removals()): AddToPlaylistViewModel {
        val viewModel = AddToPlaylistViewModel(playlists, removals, snackbars)
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

        viewModel.addTo(mine, listOf("s1", "s2"))

        assertThat(playlists.added).containsExactly("mine" to listOf("s1", "s2"))
        assertThat(viewModel.uiState.value.isDone).isTrue()
    }

    @Test
    fun addingConfirmsHowManySongsWentToWhichPlaylist() = runTest {
        viewModel().addTo(mine, listOf("s1", "s2", "s3"))

        val message = snackbars.current.value!!
        assertThat(message.text).isEqualTo(
            SnackbarText.plural(R.plurals.feature_playlist_added_to, 3, 3, "Road trip"),
        )
        assertThat(message.action!!.label).isEqualTo(SnackbarText.of(R.string.feature_playlist_view))
    }

    @Test
    fun addingSongsAlreadyThereSkipsThemAndSaysHowMany() = runTest {
        playlists.songsByPlaylist.value = mapOf("mine" to listOf(TestData.song("s1"), TestData.song("s2")))

        viewModel().addTo(mine, listOf("s1", "s2", "s3"))

        assertThat(playlists.added).containsExactly("mine" to listOf("s3"))
        assertThat(snackbars.current.value!!.text).isEqualTo(
            SnackbarText.plural(R.plurals.feature_playlist_added_to_skipping, 1, 1, "Road trip", 2),
        )
    }

    @Test
    fun addingOnlySongsAlreadyThereSaysSo() = runTest {
        playlists.songsByPlaylist.value = mapOf("mine" to listOf(TestData.song("s1"), TestData.song("s2")))

        viewModel().addTo(mine, listOf("s1", "s2"))

        assertThat(playlists.added).isEmpty()
        assertThat(snackbars.current.value!!.text).isEqualTo(
            SnackbarText.plural(R.plurals.feature_playlist_already_in, 2, 2, "Road trip"),
        )
    }

    @Test
    fun addingASongWhoseRemovalCanStillBeUndoneKeepsItInThePlaylist() = runTest {
        val song = TestData.song("s1")
        playlists.songsByPlaylist.value = mapOf("mine" to listOf(TestData.song("s0"), song))
        val removals = removals()
        removals.remove("mine", PlaylistEntry(song))

        viewModel(removals).addTo(mine, listOf("s1"))

        assertThat(playlists.removed).containsExactly("mine" to listOf(1))
        assertThat(playlists.added).containsExactly("mine" to listOf("s1"))
        assertThat(snackbars.current.value!!.text).isEqualTo(
            SnackbarText.plural(R.plurals.feature_playlist_added_to, 1, 1, "Road trip"),
        )
    }

    @Test
    fun viewOpensThePlaylist() = runTest {
        val viewModel = viewModel()
        viewModel.addTo(mine, listOf("s1"))

        snackbars.onAction(snackbars.current.value!!)

        assertThat(viewModel.playlistsToOpen.first()).isEqualTo("mine")
    }

    @Test
    fun createNewPlaylistWithSongs() = runTest {
        val viewModel = viewModel()

        viewModel.createWith("Fresh", listOf("s1", "s2"))

        val created = playlists.playlistsState.value.single { it.name == "Fresh" }
        assertThat(created.songCount).isEqualTo(2)
        assertThat(viewModel.uiState.value.isDone).isTrue()
        assertThat(snackbars.current.value!!.text).isEqualTo(
            SnackbarText.plural(R.plurals.feature_playlist_added_to, 2, 2, "Fresh"),
        )
        snackbars.onAction(snackbars.current.value!!)
        assertThat(viewModel.playlistsToOpen.first()).isEqualTo(created.id)
    }

    @Test
    fun failureShowsErrorAndResetClearsIt() = runTest {
        val viewModel = viewModel()
        playlists.error = RemoteException.Unreachable(IOException("offline"))

        viewModel.addTo(mine, listOf("s1"))
        assertThat(viewModel.uiState.value.failed).isTrue()
        assertThat(snackbars.current.value).isNull()
        assertThat(viewModel.uiState.value.isDone).isFalse()

        viewModel.reset()
        assertThat(viewModel.uiState.value.failed).isFalse()
    }
}
