package com.quantumff.segue.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.download.requestRefresh
import com.quantumff.segue.core.data.play.MediaActionHandler
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.ScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GenreUiState(
    val genre: String,
    /** Albums the grid shows; null until Room answers. */
    val albumCount: Int? = null,
    /** Online, or why the grid shows only downloaded albums: the chip under the top bar. */
    val offlineStatus: OfflineStatus = OfflineStatus.Online,
    /** Pin status by album ID, for the cards' download badges. */
    val albumDownloads: ImmutableMap<String, DownloadStatus> = persistentMapOf(),
    /** A failed menu action, for the snackbar. */
    val message: MediaActionFailure? = null,
) {
    /** Skeletons until Room answers, the grid, or an empty state: offline, a genre with nothing downloaded. */
    val screen: ScreenState get() = ScreenState.of(albumCount)
}

/** One genre's albums, from Room. The route argument is the genre name. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GenreViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    albumRepository: AlbumRepository,
    private val offlineView: OfflineLibraryView,
    private val playIntents: PlayIntents,
    private val mediaActions: MediaActionHandler,
    private val snackbars: Snackbars,
) : ViewModel() {
    val genre: String = checkNotNull(savedStateHandle.get<String>(KEY_GENRE)) { "GenreRoute needs a genre" }

    /** The genre's albums, narrowed to downloaded ones while offline. */
    private val filter = offlineView.albumFilter(flowOf(AlbumFilter(genre = genre)))

    val albums: Flow<PagingData<Album>> =
        filter.flatMapLatest { albumRepository.albums(SORT, it) }.cachedIn(viewModelScope)

    private val message = MutableStateFlow<MediaActionFailure?>(null)

    val uiState: StateFlow<GenreUiState> = combine(
        filter.flatMapLatest { albumRepository.albumCount(it) },
        offlineView.offlineStatus,
        offlineView.pinStatuses.map { it[PinKind.Album] },
        message,
    ) { count, offlineStatus, downloads, message ->
        GenreUiState(
            genre = genre,
            albumCount = count,
            offlineStatus = offlineStatus,
            albumDownloads = downloads,
            message = message,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GenreUiState(genre))

    /** "Play albums" or "Shuffle albums" of the grid as shown: the genre's albums by name. */
    fun playAlbums(order: AlbumOrder, placement: QueuePlacement) {
        val request = AlbumPlayRequest(AlbumSource.Genre(genre), order, placement)
        viewModelScope.launch { playIntents.playAlbums(request, QueueSource.Genre(genre)) }
    }

    /**
     * The offline chip's retry. The grid reads Room only, so there is no server to ask: while still
     * offline a snackbar says why only downloaded albums show, as on Home. Back online the grid has already widened.
     */
    fun retry() = offlineView.requestRefresh(snackbars) {}

    /** A card's long-press menu. */
    fun onAlbumAction(album: Album, action: MediaAction, host: MediaActionHost) {
        viewModelScope.launch { mediaActions.album(album, action, host)?.let { message.value = it } }
    }

    fun messageShown() {
        message.value = null
    }

    internal companion object {
        /** Matches the `genre` property of [com.quantumff.segue.feature.library.navigation.GenreRoute]. */
        const val KEY_GENRE = "genre"
        private val SORT = AlbumSortOrder.Name
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
