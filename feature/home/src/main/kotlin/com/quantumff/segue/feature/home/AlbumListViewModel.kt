package com.quantumff.segue.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.download.refreshIfOnline
import com.quantumff.segue.core.data.download.requestRefresh
import com.quantumff.segue.core.data.play.MediaActionHandler
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.OfflineLibrary
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.model.ScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumListUiState(
    val type: AlbumListType,
    val albums: ImmutableList<Album> = persistentListOf(),
    val isRefreshing: Boolean = false,
    /**
     * Why the grid shows less than the server has, for the chip: offline only downloaded albums
     * show; online, the last refresh may not have reached the server, so the cached list shows.
     */
    val offlineStatus: OfflineStatus = OfflineStatus.Online,
    val isLoading: Boolean = true,
    /** A failed menu action, for the snackbar. */
    val message: MediaActionFailure? = null,
) {
    /**
     * What the grid shows (DESIGN.md §5.8). With no albums, a running refresh is loading. Offline,
     * empty means nothing in this list is downloaded, which the screen says rather than "nothing here".
     */
    val screenState: ScreenState
        get() = ScreenState.of(itemCount = albums.size.takeUnless { isLoading }, isLoading = isRefreshing)
}

private data class AlbumListStatus(
    val isRefreshing: Boolean = false,
    /** The last refresh could not reach the server. */
    val serverUnreachable: Boolean = false,
    val message: MediaActionFailure? = null,
)

/**
 * "See all" for a Home section: the server-ordered list, up to [SEE_ALL_SIZE] albums. Offline it
 * narrows to downloaded albums, like the section it came from.
 */
@HiltViewModel
class AlbumListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val offlineView: OfflineLibraryView,
    private val mediaActions: MediaActionHandler,
    private val snackbars: Snackbars,
) : ViewModel() {
    private val type: AlbumListType =
        AlbumListType.valueOf(checkNotNull(savedStateHandle.get<String>(KEY_LIST_TYPE)) { "Missing list type" })

    private val status = MutableStateFlow(AlbumListStatus())

    val uiState: StateFlow<AlbumListUiState> = combine(
        status,
        albumRepository.albumList(type),
        offlineView.downloadedWhileOffline,
        offlineView.offlineStatus,
    ) { status, albums, downloaded, offlineStatus ->
        AlbumListUiState(
            type = type,
            albums = OfflineLibrary.albums(albums, downloaded).toImmutableList(),
            isRefreshing = status.isRefreshing,
            offlineStatus = offlineStatus.withServerUnreachable(status.serverUnreachable),
            isLoading = false,
            message = status.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AlbumListUiState(type = type))

    init {
        // Offline the server is not asked on entry: the downloaded albums are what there is, and the chip says so.
        offlineView.refreshIfOnline(::refreshList)
    }

    /**
     * Pull to refresh, and the chip's retry: refetches the list while Room keeps showing the cached
     * one. Offline the server is not asked, and a snackbar says why.
     */
    fun refresh() = offlineView.requestRefresh(snackbars, ::refreshList)

    private fun refreshList() {
        if (status.value.isRefreshing) return
        status.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val outcome = remoteOutcome { albumRepository.refreshAlbumList(type, SEE_ALL_SIZE) }
            status.update { it.copy(isRefreshing = false, serverUnreachable = outcome.showsOfflineChip) }
        }
    }

    fun onAlbumAction(album: Album, action: MediaAction, host: MediaActionHost) {
        viewModelScope.launch { mediaActions.album(album, action, host)?.let(::showMessage) }
    }

    fun messageShown() {
        status.update { it.copy(message = null) }
    }

    private fun showMessage(message: MediaActionFailure) {
        status.update { it.copy(message = message) }
    }

    companion object {
        /** Matches the `listType` property of the album list route. */
        const val KEY_LIST_TYPE = "listType"

        /** Navidrome caps list requests at 500; 100 is plenty for a "See all" grid. */
        const val SEE_ALL_SIZE = 100
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
