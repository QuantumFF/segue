package com.quantumff.segue.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
    val isOffline: Boolean = false,
    val isLoading: Boolean = true,
    val message: HomeMessage? = null,
)

/** "See all" for a Home section: the server-ordered list, up to [SEE_ALL_SIZE] albums. */
@HiltViewModel
class AlbumListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val starRatingRepository: StarRatingRepository,
) : ViewModel() {
    private val type: AlbumListType =
        AlbumListType.valueOf(checkNotNull(savedStateHandle.get<String>(KEY_LIST_TYPE)) { "Missing list type" })

    private val status = MutableStateFlow(AlbumListUiState(type = type))

    val uiState: StateFlow<AlbumListUiState> = combine(status, albumRepository.albumList(type)) { status, albums ->
        status.copy(albums = albums, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), status.value)

    init {
        refresh()
    }

    fun refresh() {
        if (status.value.isRefreshing) return
        status.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val offline = try {
                albumRepository.refreshAlbumList(type, SEE_ALL_SIZE)
                false
            } catch (e: RemoteException) {
                e is RemoteException.Unreachable
            }
            status.update { it.copy(isRefreshing = false, isOffline = offline) }
        }
    }

    fun withAlbumSongs(album: Album, onLoaded: (List<Song>) -> Unit) {
        viewModelScope.launch {
            try {
                val songs = albumRepository.songsForPlayback(album.id)
                if (songs.isNotEmpty()) onLoaded(songs) else showMessage(HomeMessage.PlaybackLoadFailed)
            } catch (_: RemoteException) {
                showMessage(HomeMessage.PlaybackLoadFailed)
            }
        }
    }

    fun toggleFavorite(album: Album) {
        viewModelScope.launch {
            try {
                starRatingRepository.setStarred(MediaKind.Album, album.id, !album.isStarred)
            } catch (_: RemoteException) {
                showMessage(HomeMessage.FavoriteFailed)
            }
        }
    }

    fun messageShown() {
        status.update { it.copy(message = null) }
    }

    private fun showMessage(message: HomeMessage) {
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
