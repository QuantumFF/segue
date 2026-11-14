package com.quantumff.segue.feature.playlist

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.R as DataR
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.snackbar.SnackbarMessage
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.data.snackbar.offline
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.HeroPlayback
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.model.PinAction
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.SongDownload
import com.quantumff.segue.core.testing.MainDispatcherRule
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.play.FakePlayerStateSource
import com.quantumff.segue.core.testing.play.RecordingPlayIntents
import com.quantumff.segue.core.testing.play.RecordingPlayerTransport
import com.quantumff.segue.core.testing.play.mediaActionHandler
import com.quantumff.segue.core.testing.repository.FakeDownloadRepository
import com.quantumff.segue.core.testing.repository.FakeOfflineModeRepository
import com.quantumff.segue.core.testing.repository.FakePlaylistRepository
import com.quantumff.segue.core.testing.repository.FakeStarRatingRepository
import com.quantumff.segue.core.testing.repository.offlineLibraryView
import java.io.IOException
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PlaylistViewModelTest {
    private val downloads = FakeDownloadRepository()
    private val offlineMode = FakeOfflineModeRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playlists = FakePlaylistRepository().apply {
        playlistsState.value = listOf(TestData.playlist("p1", songCount = 3))
        songsByPlaylist.value = mapOf("p1" to listOf(TestData.song("a"), TestData.song("b"), TestData.song("c")))
    }
    private val stars = FakeStarRatingRepository()
    private val play = RecordingPlayIntents()
    private val player = FakePlayerStateSource()
    private val transport = RecordingPlayerTransport()
    private val snackbars = Snackbars()

    private fun TestScope.viewModel(): PlaylistViewModel {
        val viewModel = PlaylistViewModel(
            SavedStateHandle(mapOf("playlistId" to "p1")),
            playlists,
            downloads,
            offlineLibraryView(offlineMode, downloads, playlists = playlists),
            play,
            player,
            transport,
            mediaActionHandler(play, downloads, stars, playlists = playlists),
            PlaylistRemovals(playlists, snackbars, backgroundScope),
            snackbars,
        )
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

    private val PlaylistViewModel.marks
        get() = uiState.value.let { state ->
            state.entries.map { state.trackRow(it) }.map { it.isCurrent to it.isPlaying }
        }

    @Test
    fun theCurrentSongsRowsAreMarkedEvenWhenListedTwice() = runTest {
        playlists.songsByPlaylist.value =
            mapOf("p1" to listOf(TestData.song("a"), TestData.song("b"), TestData.song("a")))
        val viewModel = viewModel()
        assertThat(viewModel.marks).containsExactly(false to false, false to false, false to false).inOrder()

        player.state.value = TestData.playerOn("a", isPlaying = true)
        assertThat(viewModel.marks).containsExactly(true to true, false to false, true to true).inOrder()

        player.state.value = TestData.playerOn("a", isPlaying = false)
        assertThat(viewModel.marks).containsExactly(true to false, false to false, true to false).inOrder()

        player.state.value = TestData.playerOn("b", isPlaying = true)
        assertThat(viewModel.marks).containsExactly(false to false, true to true, false to false).inOrder()
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
    fun moveDownAndUpSwapNeighbours() = runTest {
        val viewModel = viewModel()
        viewModel.startEditing()

        viewModel.move(from = 0, to = 1)
        assertThat(viewModel.songIds).containsExactly("b", "a", "c").inOrder()
        assertThat(viewModel.uiState.value.hasChanges).isTrue()

        viewModel.move(from = 1, to = 0)
        assertThat(viewModel.songIds).containsExactly("a", "b", "c").inOrder()
        assertThat(viewModel.uiState.value.hasChanges).isFalse()

        viewModel.move(from = 2, to = 1)
        assertThat(viewModel.songIds).containsExactly("a", "c", "b").inOrder()
    }

    @Test
    fun moveOutOfRangeOrInPlaceIsIgnored() = runTest {
        val viewModel = viewModel()
        viewModel.startEditing()

        viewModel.move(from = 0, to = -1)
        viewModel.move(from = 2, to = 3)
        viewModel.move(from = 3, to = 0)
        viewModel.move(from = 1, to = 1)

        assertThat(viewModel.songIds).containsExactly("a", "b", "c").inOrder()
        assertThat(viewModel.uiState.value.hasChanges).isFalse()
    }

    @Test
    fun moveAndRemoveOutsideEditModeAreIgnored() = runTest {
        val viewModel = viewModel()

        viewModel.move(from = 0, to = 1)
        viewModel.removeEntry(viewModel.uiState.value.entries[0].key)

        assertThat(viewModel.songIds).containsExactly("a", "b", "c").inOrder()
        assertThat(viewModel.uiState.value.isEditing).isFalse()
    }

    @Test
    fun removeEntryRemovesOnlyThatCopyOfADuplicateSong() = runTest {
        playlists.songsByPlaylist.value =
            mapOf("p1" to listOf(TestData.song("a"), TestData.song("b"), TestData.song("a")))
        val viewModel = viewModel()
        viewModel.startEditing()

        viewModel.removeEntry(viewModel.uiState.value.entries[2].key)

        assertThat(viewModel.songIds).containsExactly("a", "b").inOrder()
        assertThat(viewModel.uiState.value.hasChanges).isTrue()
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
    fun saveIsEnabledOnlyWhileTheWorkingOrderDiffersFromTheSavedOne() = runTest {
        val viewModel = viewModel()

        viewModel.startEditing()
        assertThat(viewModel.uiState.value.canSave).isFalse()

        viewModel.move(from = 0, to = 2)
        assertThat(viewModel.uiState.value.canSave).isTrue()

        viewModel.move(from = 2, to = 0)
        assertThat(viewModel.uiState.value.canSave).isFalse()

        viewModel.removeEntry(viewModel.uiState.value.entries[1].key)
        assertThat(viewModel.uiState.value.canSave).isTrue()
    }

    @Test
    fun backWithoutChangesLeavesEditModeWithoutAsking() = runTest {
        val viewModel = viewModel()
        viewModel.startEditing()
        viewModel.move(from = 0, to = 1)
        viewModel.move(from = 1, to = 0)

        viewModel.onEditBack()

        val state = viewModel.uiState.value
        assertThat(state.isEditing).isFalse()
        assertThat(state.isDiscardPromptShown).isFalse()
    }

    @Test
    fun backWithChangesAsksAndKeepEditingKeepsTheEdits() = runTest {
        val viewModel = viewModel()
        viewModel.startEditing()
        viewModel.move(from = 0, to = 2)

        viewModel.onEditBack()
        assertThat(viewModel.uiState.value.isDiscardPromptShown).isTrue()
        assertThat(viewModel.uiState.value.isEditing).isTrue()

        viewModel.keepEditing()

        val state = viewModel.uiState.value
        assertThat(state.isDiscardPromptShown).isFalse()
        assertThat(state.isEditing).isTrue()
        assertThat(viewModel.songIds).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun backWithChangesThenDiscardLeavesEditModeWithoutSaving() = runTest {
        val viewModel = viewModel()
        viewModel.startEditing()
        viewModel.move(from = 0, to = 2)
        viewModel.onEditBack()

        viewModel.cancelEditing()

        val state = viewModel.uiState.value
        assertThat(state.isDiscardPromptShown).isFalse()
        assertThat(state.isEditing).isFalse()
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
    fun deleteMarksDeletedAndSaysSoInTheAppsSnackbars() = runTest {
        val viewModel = viewModel()

        viewModel.delete()

        assertThat(playlists.deleted).containsExactly("p1")
        assertThat(viewModel.uiState.value.isDeleted).isTrue()
        // Posted app-wide, so it outlives this screen closing; it has no Undo.
        assertThat(shown.text).isEqualTo(SnackbarText.of(R.string.feature_playlist_deleted, "Playlist p1"))
        assertThat(shown.action).isNull()
    }

    @Test
    fun aFailedDeleteKeepsThePlaylistAndSaysWhy() = runTest {
        val viewModel = viewModel()
        playlists.error = RemoteException.Unreachable(IOException("offline"))

        viewModel.delete()

        assertThat(viewModel.uiState.value.isDeleted).isFalse()
        assertThat(viewModel.uiState.value.message).isEqualTo(PlaylistMessage.Failed)
        assertThat(snackbars.current.value).isNull()
    }

    /** The snackbar showing now. */
    private val shown: SnackbarMessage get() = checkNotNull(snackbars.current.value)

    @Test
    fun removeFromMenuThenUndoNeverWritesAndPutsTheSongBackInPlace() = runTest {
        val viewModel = viewModel()

        viewModel.removeAt(1)
        assertThat(viewModel.songIds).containsExactly("a", "c").inOrder()
        assertThat(shown.text).isEqualTo(SnackbarText.of(R.string.feature_playlist_removed, "Song b"))

        snackbars.onAction(shown)

        assertThat(viewModel.songIds).containsExactly("a", "b", "c").inOrder()
        assertThat(playlists.removed).isEmpty()
    }

    @Test
    fun removeFromMenuIsWrittenOnceItsSnackbarIsDismissedOrTimesOut() = runTest {
        val viewModel = viewModel()

        viewModel.removeAt(1)
        assertThat(playlists.removed).isEmpty()

        snackbars.onDismiss(shown)

        assertThat(playlists.removed).containsExactly("p1" to listOf(1))
        assertThat(viewModel.songIds).containsExactly("a", "c").inOrder()
    }

    @Test
    fun aNewerRemovalWritesTheEarlierOneAndEachFindsItsSong() = runTest {
        playlists.songsByPlaylist.value = mapOf("p1" to listOf("a", "b", "c", "d").map(TestData::song))
        val viewModel = viewModel()

        viewModel.removeAt(1)
        viewModel.removeAt(2)
        assertThat(viewModel.songIds).containsExactly("a", "c").inOrder()
        // "Removed Song b" came down when "Removed Song d" was posted, and b was written.
        assertThat(shown.text).isEqualTo(SnackbarText.of(R.string.feature_playlist_removed, "Song d"))
        assertThat(playlists.removed).containsExactly("p1" to listOf(1))

        snackbars.onDismiss(shown)

        // b's write moved d up from position 3 to 2.
        assertThat(playlists.removed).containsExactly("p1" to listOf(1), "p1" to listOf(2)).inOrder()
        assertThat(playlists.songsByPlaylist.value["p1"]!!.map { it.id }).containsExactly("a", "c").inOrder()
    }

    @Test
    fun undoingTheNewerRemovalKeepsItsSongWhileTheEarlierOneIsWritten() = runTest {
        val viewModel = viewModel()

        viewModel.removeAt(0)
        viewModel.removeAt(1)
        snackbars.onAction(shown)

        assertThat(playlists.removed).containsExactly("p1" to listOf(0))
        assertThat(viewModel.songIds).containsExactly("b", "c").inOrder()
    }

    @Test
    fun removingBothCopiesOfASongRemovesEachOnce() = runTest {
        playlists.songsByPlaylist.value = mapOf("p1" to listOf("x", "a", "x").map(TestData::song))
        val viewModel = viewModel()

        viewModel.removeAt(0)
        viewModel.removeAt(1)
        assertThat(viewModel.songIds).containsExactly("a")
        snackbars.onDismiss(shown)

        // The first copy's write made the second copy the first, at position 1.
        assertThat(playlists.removed).containsExactly("p1" to listOf(0), "p1" to listOf(1)).inOrder()
        assertThat(playlists.songsByPlaylist.value["p1"]!!.map { it.id }).containsExactly("a")
    }

    @Test
    fun editingWritesThePendingRemovalBeforeSavingTheNewOrder() = runTest {
        val viewModel = viewModel()
        viewModel.removeAt(1)

        viewModel.startEditing()
        assertThat(snackbars.current.value).isNull()
        viewModel.move(from = 1, to = 0)
        viewModel.save()

        assertThat(playlists.removed).containsExactly("p1" to listOf(1))
        assertThat(playlists.replaced).containsExactly("p1" to listOf("c", "a"))
        assertThat(playlists.songsByPlaylist.value["p1"]!!.map { it.id }).containsExactly("c", "a").inOrder()
    }

    @Test
    fun aFailedRemovalWriteBringsTheSongBack() = runTest {
        val viewModel = viewModel()
        viewModel.removeAt(1)
        playlists.error = RemoteException.Unreachable(IOException("offline"))

        snackbars.onDismiss(shown)

        assertThat(viewModel.songIds).containsExactly("a", "b", "c").inOrder()
        assertThat(shown.text).isEqualTo(SnackbarText.of(R.string.feature_playlist_error_generic))
    }

    @Test
    fun removeFromMenuRemovesThatPosition() = runTest {
        val viewModel = viewModel()

        viewModel.removeAt(1)

        assertThat(viewModel.songIds).containsExactly("a", "c").inOrder()
    }

    @Test
    fun withNothingCachedTheFirstRefreshShowsLoading() = runTest {
        playlists.playlistsState.value = emptyList()
        playlists.songsByPlaylist.value = emptyMap()
        val refresh = CompletableDeferred<Unit>()
        playlists.playlistRefreshGate = refresh
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(LoadState.Loading)

        playlists.playlistsState.value = listOf(TestData.playlist("p1"))
        refresh.complete(Unit)
        assertThat(viewModel.uiState.value.loadState).isEqualTo(LoadState.Loaded)
    }

    @Test
    fun aFailedFirstRefreshShowsAnErrorThatRetryClears() = runTest {
        playlists.playlistsState.value = emptyList()
        playlists.error = RemoteException.ServerError("broken", IOException("500"))
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.loadState).isEqualTo(LoadState.Failed)

        playlists.error = null
        playlists.playlistsState.value = listOf(TestData.playlist("p1"))
        viewModel.refresh()

        assertThat(viewModel.uiState.value.loadState).isEqualTo(LoadState.Loaded)
    }

    @Test
    fun aPlaylistTheServerDoesNotHaveIsNotFound() = runTest {
        playlists.playlistsState.value = emptyList()
        playlists.error = RemoteException.NotFound(IOException("70"))

        assertThat(viewModel().uiState.value.loadState).isEqualTo(LoadState.NotFound)
    }

    @Test
    fun aRefreshThatLeavesNothingCachedIsNotFoundRatherThanLoadingForever() = runTest {
        playlists.playlistsState.value = emptyList()
        playlists.songsByPlaylist.value = emptyMap()

        val state = viewModel().uiState.value

        assertThat(state.loadState).isEqualTo(LoadState.NotFound)
        assertThat(state.isRefreshing).isFalse()
    }

    @Test
    fun theFirstRefreshCountsAsRunningBeforeItEnds() = runTest {
        playlists.playlistRefreshGate = CompletableDeferred()

        val state = viewModel().uiState.value

        assertThat(state.isRefreshing).isTrue()
        assertThat(state.isEmpty).isFalse()
    }

    @Test
    fun aDeletedPlaylistIsNotFoundEvenWhileStillCached() = runTest {
        playlists.error = RemoteException.NotFound(IOException("70"))

        assertThat(viewModel().uiState.value.loadState).isEqualTo(LoadState.NotFound)
    }

    @Test
    fun aPlaylistWithNoSongsIsEmptyOnceItsRefreshEnds() = runTest {
        playlists.songsByPlaylist.value = emptyMap()
        val refresh = CompletableDeferred<Unit>()
        playlists.playlistRefreshGate = refresh
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.isEmpty).isFalse()

        refresh.complete(Unit)

        assertThat(viewModel.uiState.value.isEmpty).isTrue()
    }

    @Test
    fun aPlaylistWithSongsIsNotEmpty() = runTest {
        assertThat(viewModel().uiState.value.isEmpty).isFalse()
    }

    @Test
    fun offlineThePlaylistCanPlayOnlyWithADownloadedSong() = runTest {
        offlineMode.isOffline.value = true
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.canPlay).isFalse()

        downloads.songDownloads.value = persistentMapOf("b" to SongDownload("b", DownloadState.Completed, 1f))

        assertThat(viewModel.uiState.value.canPlay).isTrue()
    }

    @Test
    fun anEmptyPlaylistCannotPlay() = runTest {
        playlists.songsByPlaylist.value = emptyMap()

        assertThat(viewModel().uiState.value.canPlay).isFalse()
    }

    @Test
    fun whileEditingThePlaylistCannotPlay() = runTest {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.canPlay).isTrue()

        viewModel.startEditing()

        assertThat(viewModel.uiState.value.canPlay).isFalse()
    }

    @Test
    fun refreshFailureWithCachedDataKeepsTheRowsWithTheOfflineChip() = runTest {
        playlists.error = RemoteException.Unreachable(IOException("offline"))

        val state = viewModel().uiState.value

        assertThat(state.loadState).isEqualTo(LoadState.Loaded)
        assertThat(state.offlineStatus).isEqualTo(OfflineStatus.ShowingCachedLibrary)
        assertThat(state.playlist).isNotNull()
        assertThat(state.entries).hasSize(3)
    }

    @Test
    fun serverErrorWithCachedDataKeepsTheRowsWithoutTheOfflineChip() = runTest {
        playlists.error = RemoteException.ServerError("broken", IOException("500"))

        val state = viewModel().uiState.value

        assertThat(state.loadState).isEqualTo(LoadState.Loaded)
        assertThat(state.offlineStatus).isEqualTo(OfflineStatus.Online)
        assertThat(state.entries).hasSize(3)
    }

    @Test
    fun offlineWithoutANetworkShowsTheDownloadsStatus() = runTest {
        offlineMode.isOffline.value = true
        playlists.error = RemoteException.Unreachable(IOException("offline"))

        assertThat(viewModel().uiState.value.offlineStatus).isEqualTo(OfflineStatus.ShowingDownloads)
    }

    @Test
    fun offlineModeShowsItsOwnStatus() = runTest {
        offlineMode.setOfflineMode(true)

        assertThat(viewModel().uiState.value.offlineStatus).isEqualTo(OfflineStatus.ShowingDownloadsInOfflineMode)
    }

    @Test
    fun retryWhileOfflineSaysSoWithoutAskingTheServer() = runTest {
        val viewModel = viewModel()
        offlineMode.isOffline.value = true
        val refreshes = playlists.refreshed.size

        viewModel.retry()

        assertThat(playlists.refreshed).hasSize(refreshes)
        assertThat(snackbars.current.value?.text).isEqualTo(SnackbarText.offline)
    }

    @Test
    fun retryOnlineRefetchesThePlaylist() = runTest {
        val viewModel = viewModel()
        val refreshes = playlists.refreshed.size

        viewModel.retry()

        assertThat(playlists.refreshed).hasSize(refreshes + 1)
    }

    @Test
    fun playPlaysTheRowsAsShown() = runTest {
        val viewModel = viewModel()

        viewModel.play(startIndex = 1)

        assertThat(play.plays.single().target).isEqualTo(PlayTarget.Songs(viewModel.uiState.value.songs, 1))
    }

    @Test
    fun playAndShuffleAlbumsNameThePlaylistAsTheQueueSource() = runTest {
        val viewModel = viewModel()

        viewModel.play(shuffle = true)
        viewModel.shuffleAlbums()

        val source = QueueSource.Playlist("p1", "Playlist p1")
        assertThat(play.plays.single().source).isEqualTo(source)
        assertThat(play.albumPlays.single().source).isEqualTo(source)
    }

    @Test
    fun theHeroShowsPauseOnlyWhileThisPlaylistIsThePlayingSource() = runTest {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.heroPlayback).isEqualTo(HeroPlayback.NotSource)

        player.state.value = playing(QueueSource.Playlist("p1", "Playlist p1"), isPlaying = true)
        assertThat(viewModel.uiState.value.heroPlayback).isEqualTo(HeroPlayback.Playing)

        player.state.value = playing(QueueSource.Playlist("p1", "Playlist p1"), isPlaying = false)
        assertThat(viewModel.uiState.value.heroPlayback).isEqualTo(HeroPlayback.Paused)

        // Ids are unique per kind only: an album with this playlist's id is another source.
        player.state.value = playing(QueueSource.Album("p1", "Album p1"), isPlaying = true)
        assertThat(viewModel.uiState.value.heroPlayback).isEqualTo(HeroPlayback.NotSource)

        player.state.value = playing(QueueSource.Playlist("p2", "Playlist p2"), isPlaying = true)
        assertThat(viewModel.uiState.value.heroPlayback).isEqualTo(HeroPlayback.NotSource)
    }

    @Test
    fun heroPlayPausesThePlayingPlaylistResumesItWhenPausedAndOtherwiseReplacesTheQueue() = runTest {
        val viewModel = viewModel()
        val thisPlaylist = QueueSource.Playlist("p1", "Playlist p1")

        player.state.value = playing(thisPlaylist, isPlaying = true)
        viewModel.onHeroPlay()
        player.state.value = playing(thisPlaylist, isPlaying = false)
        viewModel.onHeroPlay()

        assertThat(transport.commands).containsExactly(
            RecordingPlayerTransport.Command.Pause,
            RecordingPlayerTransport.Command.Play,
        ).inOrder()
        assertThat(play.intents).isEmpty()

        player.state.value = playing(QueueSource.Library(shuffled = true), isPlaying = true)
        viewModel.onHeroPlay()

        val replace = play.plays.single()
        assertThat(replace.target).isEqualTo(PlayTarget.Songs(viewModel.uiState.value.songs, 0))
        assertThat(replace.shuffle).isFalse()
        assertThat(replace.source).isEqualTo(thisPlaylist)
        assertThat(transport.commands).hasSize(2)
    }

    @Test
    fun heroPlayAfterThisPlaylistPlayedToTheEndPlaysItAgainFromTheStart() = runTest {
        val viewModel = viewModel()
        val thisPlaylist = QueueSource.Playlist("p1", "Playlist p1")

        player.state.value = playing(thisPlaylist, isPlaying = false).copy(hasEnded = true)
        assertThat(viewModel.uiState.value.heroPlayback).isEqualTo(HeroPlayback.NotSource)
        viewModel.onHeroPlay()

        val replace = play.plays.single()
        assertThat(replace.target).isEqualTo(PlayTarget.Songs(viewModel.uiState.value.songs, 0))
        assertThat(replace.source).isEqualTo(thisPlaylist)
        assertThat(transport.commands).isEmpty()
    }

    /** The player on a song of [source], which is the queue source. */
    private fun playing(source: QueueSource, isPlaying: Boolean) =
        TestData.playerOn("elsewhere", isPlaying).copy(queueSource = source)

    @Test
    fun swipingATrackQueuesItWithoutAConfirmation() = runTest {
        val viewModel = viewModel()
        val song = TestData.song("s1")

        viewModel.onSongSwipe(song, QueuePlacement.Next)
        viewModel.onSongSwipe(song, QueuePlacement.End)

        assertThat(play.plays).containsExactly(
            RecordingPlayIntents.Play(PlayTarget.Songs(listOf(song)), QueuePlacement.Next, false, confirm = false),
            RecordingPlayIntents.Play(PlayTarget.Songs(listOf(song)), QueuePlacement.End, false, confirm = false),
        ).inOrder()
    }

    @Test
    fun trackMenuStarsAndRemovesFromThePlaylist() = runTest {
        val viewModel = viewModel()

        viewModel.onSongAction(TestData.song("a"), MediaAction.Favorite, MediaActionHost())
        viewModel.onSongAction(
            TestData.song("b"),
            MediaAction.RemoveFromPlaylist,
            MediaActionHost(onScreenAction = { viewModel.removeAt(1) }),
        )

        assertThat(stars.starred[MediaKind.Song to "a"]).isTrue()
        assertThat(viewModel.songIds).containsExactly("a", "c").inOrder()
    }

    @Test
    fun heroDownloadConfirmsWithThePlaylistNameAndRemoveDownloadOffersUndo() = runTest {
        val viewModel = viewModel()
        val name = "Playlist p1"

        viewModel.download()
        val downloading = checkNotNull(snackbars.current.value)
        assertThat(downloading.text).isEqualTo(SnackbarText.of(DataR.string.core_data_downloading_name, name))
        snackbars.onDismiss(downloading)

        viewModel.onPinAction(PinAction.Remove)
        assertThat(downloads.pins.value).isEmpty()
        val removed = checkNotNull(snackbars.current.value)
        assertThat(removed.text).isEqualTo(SnackbarText.of(DataR.string.core_data_download_removed, name))

        snackbars.onAction(removed)
        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Playlist to "p1")
        assertThat(downloads.deleted).isEmpty()
    }

    @Test
    fun theDownloadSheetPausesResumesAndRetriesThePlaylistsPin() = runTest {
        downloads.pins.value = mapOf((PinKind.Playlist to "p1") to DownloadStatus.Downloading(0.2f))
        val viewModel = viewModel()

        viewModel.onPinAction(PinAction.Pause)
        viewModel.onPinAction(PinAction.Resume)
        viewModel.onPinAction(PinAction.Retry)

        assertThat(downloads.paused).containsExactly(PinKind.Playlist to "p1")
        assertThat(downloads.resumed).containsExactly(PinKind.Playlist to "p1", PinKind.Playlist to "p1")
    }

    @Test
    fun aSharedPlaylistsMenuQueuesItAddsItToAPlaylistAndDownloadsIt() = runTest {
        playlists.playlistsState.value = listOf(TestData.playlist("p1", songCount = 3, readonly = true))
        val viewModel = viewModel()
        val addedToPlaylist = mutableListOf<List<String>>()
        val host = MediaActionHost(onAddToPlaylist = { addedToPlaylist += it })

        assertThat(viewModel.uiState.value.menuActions).containsExactly(
            MediaAction.Play,
            MediaAction.Shuffle,
            MediaAction.ShuffleAlbums,
            MediaAction.PlayNext,
            MediaAction.AddToQueue,
            MediaAction.AddToPlaylist,
            MediaAction.Download,
        ).inOrder()

        viewModel.onPlaylistAction(MediaAction.PlayNext, host)
        viewModel.onPlaylistAction(MediaAction.AddToQueue, host)
        viewModel.onPlaylistAction(MediaAction.AddToPlaylist, host)
        viewModel.onPlaylistAction(MediaAction.Download, host)

        val source = QueueSource.Playlist("p1", "Playlist p1")
        assertThat(play.plays).containsExactly(
            RecordingPlayIntents.Play(PlayTarget.Playlist("p1"), QueuePlacement.Next, false, source),
            RecordingPlayIntents.Play(PlayTarget.Playlist("p1"), QueuePlacement.End, false, source),
        ).inOrder()
        assertThat(addedToPlaylist).containsExactly(listOf("a", "b", "c"))
        assertThat(downloads.pins.value.keys).containsExactly(PinKind.Playlist to "p1")
        assertThat(viewModel.uiState.value.menuActions).contains(MediaAction.RemoveDownload)
    }

    @Test
    fun anOwnedPlaylistsMenuAddsRenameAndDeleteWhichComeBackToTheScreen() = runTest {
        val viewModel = viewModel()
        val screenActions = mutableListOf<MediaAction>()
        val host = MediaActionHost(onScreenAction = { screenActions += it })

        assertThat(viewModel.uiState.value.menuActions).containsExactly(
            MediaAction.Play,
            MediaAction.Shuffle,
            MediaAction.ShuffleAlbums,
            MediaAction.PlayNext,
            MediaAction.AddToQueue,
            MediaAction.AddToPlaylist,
            MediaAction.Download,
            MediaAction.Rename,
            MediaAction.Delete,
        ).inOrder()

        viewModel.onPlaylistAction(MediaAction.Rename, host)
        viewModel.onPlaylistAction(MediaAction.Delete, host)
        viewModel.onPlaylistAction(MediaAction.PlayNext, host)

        assertThat(screenActions).containsExactly(MediaAction.Rename, MediaAction.Delete).inOrder()
        assertThat(play.plays.single().placement).isEqualTo(QueuePlacement.Next)
    }
}
