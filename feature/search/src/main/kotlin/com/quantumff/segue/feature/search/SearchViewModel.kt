package com.quantumff.segue.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.play.MediaActionHandler
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.play.PlayerStateSource
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.SearchRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.CurrentSong
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.OfflineLibrary
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.currentSong
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Result filter chips (DESIGN.md §4.6). Playlists are not searched by the repository. */
enum class SearchFilter {
    All,
    Songs,
    Albums,
    Artists,
}

/** Where the search for the current query stands. */
sealed interface SearchResults {
    /** Blank query: show recent searches and genres. */
    data object Idle : SearchResults

    /** Typed, nothing back yet. */
    data object Loading : SearchResults

    /**
     * [serverPending]: only local results so far, the server search is still running.
     * [offline]: the server search failed or was skipped (offline), so these are local (Room FTS)
     * results only; while offline they are narrowed to downloaded music.
     */
    data class Ready(val result: SearchResult, val serverPending: Boolean, val offline: Boolean) : SearchResults
}

data class SearchUiState(
    val filter: SearchFilter = SearchFilter.All,
    val results: SearchResults = SearchResults.Idle,
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val genres: ImmutableList<Genre> = persistentListOf(),
    /** A failed menu action for a snackbar, cleared with [SearchViewModel.onMessageDismiss]. */
    val message: MediaActionFailure? = null,
    /** The song that is playing, whose result rows are marked; null when nothing is current. */
    val currentSong: CurrentSong? = null,
) {
    /** The songs of the results, with favorites toggled here applied. Play intents index into this. */
    val songs: ImmutableList<Song>
        get() = (results as? SearchResults.Ready)?.result?.songs ?: persistentListOf()

    /** Whether [song]'s row is marked as the current song. */
    fun isCurrent(song: Song): Boolean = currentSong?.isOf(song) == true
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val preferences: UserPreferencesRepository,
    libraryRepository: LibraryRepository,
    offlineView: OfflineLibraryView,
    private val playIntents: PlayIntents,
    private val mediaActions: MediaActionHandler,
    player: PlayerStateSource,
) : ViewModel() {
    private val queryState = MutableStateFlow("")

    /** The text field's value. Kept apart from [uiState] so typing is never delayed by the search pipeline. */
    val query: StateFlow<String> = queryState.asStateFlow()

    private val filter = MutableStateFlow(SearchFilter.All)
    private val message = MutableStateFlow<MediaActionFailure?>(null)

    /** Song ID to starred, for favorites toggled since the results arrived (results are a snapshot). */
    private val starOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val results: Flow<SearchResults> = combine(
        queryState.map { it.trim() }.distinctUntilChanged(),
        offlineView.downloadedWhileOffline,
        ::Pair,
    )
        // Every keystroke searches Room at once; the server is asked after a 300ms pause (see search()).
        .flatMapLatest { (query, offline) ->
            when {
                query.isEmpty() -> flowOf(SearchResults.Idle)
                offline != null -> searchOffline(query, offline)
                else -> search(query)
            }
        }

    private val currentSong: Flow<CurrentSong?> = player.state.map { it.currentSong }.distinctUntilChanged()

    val uiState: StateFlow<SearchUiState> = combine(
        filter,
        combine(results, starOverrides, ::applyOverrides),
        preferences.recentSearches,
        libraryRepository.genres,
        combine(message, currentSong, ::Pair),
    ) { filter, results, recents, genres, (message, currentSong) ->
        SearchUiState(
            filter = filter,
            results = results,
            recentSearches = recents,
            genres = genres,
            message = message,
            currentSong = currentSong,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState())

    fun onQueryChange(query: String) {
        queryState.value = query
        if (query.isBlank()) filter.value = SearchFilter.All
    }

    fun onFilterChange(value: SearchFilter) {
        filter.value = value
    }

    /** The IME search action. */
    fun onSubmit() = saveRecentSearch()

    /** A result was opened or played: remember the query. */
    fun onResultOpened() = saveRecentSearch()

    fun onRecentSearchClick(query: String) {
        queryState.value = query
    }

    fun onRemoveRecentSearch(query: String) {
        viewModelScope.launch { preferences.removeRecentSearch(query) }
    }

    /** A song result's tap: plays the results' songs from that one. */
    fun onSongClick(index: Int) {
        saveRecentSearch()
        val songs = uiState.value.songs
        if (index in songs.indices) viewModelScope.launch { playIntents.play(PlayTarget.Songs(songs, index)) }
    }

    /**
     * A song's ⋮ menu ([MediaActionHandler]). Favorite shows at once, since the results are a
     * snapshot, and is undone if the server refuses it.
     */
    fun onSongAction(song: Song, action: MediaAction, host: MediaActionHost) {
        val starring = action == MediaAction.Favorite || action == MediaAction.Unfavorite
        if (starring) starOverrides.update { it + (song.id to (action == MediaAction.Favorite)) }
        viewModelScope.launch {
            val failure = mediaActions.song(song, action, host) ?: return@launch
            if (failure == MediaActionFailure.FavoriteFailed) starOverrides.update { it + (song.id to song.isStarred) }
            message.value = failure
        }
    }

    /** A track row's swipe: Play next or Add to queue ([placement]), confirming nothing (DESIGN.md §5.2). */
    fun onSongSwipe(song: Song, placement: QueuePlacement) {
        viewModelScope.launch { mediaActions.swipeSong(song, placement) }
    }

    fun onMessageDismiss() {
        message.value = null
    }

    private fun saveRecentSearch() {
        val query = queryState.value.trim()
        if (query.isEmpty()) return
        viewModelScope.launch { preferences.addRecentSearch(query) }
    }

    private fun search(query: String): Flow<SearchResults> = flow {
        emit(SearchResults.Loading)
        // Local FTS results instantly; flatMapLatest cancels this flow on the next keystroke, so
        // the server search below only runs once typing pauses for DEBOUNCE_MILLIS.
        var last: SearchResult? = searchRepository.searchLocal(query).firstOrNull()
        last?.let { emit(SearchResults.Ready(it, serverPending = true, offline = false)) }
        delay(DEBOUNCE_MILLIS)
        searchRepository.search(query)
            .catch { if (it !is RemoteException) throw it }
            .collect { result ->
                last = result
                emit(SearchResults.Ready(result, serverPending = !result.includesServerResults, offline = false))
            }
        val final = last ?: SearchResult.Empty
        // The flow finished without server results: the server search failed or we are offline.
        if (!final.includesServerResults) emit(SearchResults.Ready(final, serverPending = false, offline = true))
    }

    /** Offline: Room only, narrowed to [downloaded] music; the server is not asked. */
    private fun searchOffline(query: String, downloaded: DownloadedLibrary): Flow<SearchResults> = flow {
        val local = searchRepository.searchLocal(query).firstOrNull() ?: SearchResult.Empty
        emit(SearchResults.Ready(OfflineLibrary.search(local, downloaded), serverPending = false, offline = true))
    }

    private fun applyOverrides(results: SearchResults, overrides: Map<String, Boolean>): SearchResults {
        if (results !is SearchResults.Ready || overrides.isEmpty()) return results
        val songs = results.result.songs.map { song ->
            val starred = overrides[song.id] ?: return@map song
            when {
                starred == song.isStarred -> song
                starred -> song.copy(starredAt = System.currentTimeMillis())
                else -> song.copy(starredAt = null)
            }
        }
        return results.copy(result = results.result.copy(songs = songs.toImmutableList()))
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
