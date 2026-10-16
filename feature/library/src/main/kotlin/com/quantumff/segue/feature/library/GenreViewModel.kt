package com.quantumff.segue.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.QueuePlacement
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class GenreUiState(val genre: String, val albumCount: Int? = null)

/** One genre's albums, from Room. The route argument is the genre name. */
@HiltViewModel
class GenreViewModel @Inject constructor(savedStateHandle: SavedStateHandle, albumRepository: AlbumRepository) :
    ViewModel() {
    val genre: String = checkNotNull(savedStateHandle.get<String>(KEY_GENRE)) { "GenreRoute needs a genre" }

    private val filter = AlbumFilter(genre = genre)

    val albums: Flow<PagingData<Album>> = albumRepository.albums(SORT, filter).cachedIn(viewModelScope)

    val uiState: StateFlow<GenreUiState> = albumRepository.albumCount(filter)
        .map { GenreUiState(genre = genre, albumCount = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GenreUiState(genre))

    /** "Play albums" or "Shuffle albums" of the grid as shown: the genre's albums by name. */
    fun playAlbums(order: AlbumOrder, placement: QueuePlacement, playActions: PlayActions) =
        playActions.onPlayAlbums(AlbumPlayRequest(AlbumSource.Albums(SORT, filter), order, placement))

    internal companion object {
        /** Matches the `genre` property of [com.quantumff.segue.feature.library.navigation.GenreRoute]. */
        const val KEY_GENRE = "genre"
        private val SORT = AlbumSortOrder.Name
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
