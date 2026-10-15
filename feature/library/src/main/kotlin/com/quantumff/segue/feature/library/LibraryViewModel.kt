package com.quantumff.segue.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.LibrarySyncRepository
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Library tabs, in DESIGN.md §4.2 order. */
enum class LibraryTab {
    Albums,
    Artists,
    Songs,
    Playlists,
    Genres,
    Downloads,
}

enum class LibraryViewMode {
    Grid,
    List,
}

/** One-shot messages shown in a snackbar. */
enum class LibraryMessage {
    LoadFailed,
    FavoriteFailed,
    RefreshFailed,
    NothingToPlay,
}

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.Albums,
    val sort: AlbumSortOrder = AlbumSortOrder.RecentlyAdded,
    val viewMode: LibraryViewMode = LibraryViewMode.Grid,
    val filter: AlbumFilter = AlbumFilter(),
    val serverName: String? = null,
    /** Null until counted, so an empty state never flashes before Room answers. */
    val albumCount: Int? = null,
    val artistCount: Int? = null,
    val songCount: Int? = null,
    val genres: ImmutableList<Genre> = persistentListOf(),
    val years: ImmutableList<Int> = persistentListOf(),
    val playlists: ImmutableList<Playlist>? = null,
    val syncProgress: SyncProgress = SyncProgress.Idle,
    val isRefreshing: Boolean = false,
    val message: LibraryMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val libraryRepository: LibraryRepository,
    playlistRepository: PlaylistRepository,
    private val syncRepository: LibrarySyncRepository,
    private val starRatingRepository: StarRatingRepository,
    serverRepository: ServerRepository,
) : ViewModel() {
    private val tab = savedStateHandle.getStateFlow(KEY_TAB, LibraryTab.Albums)
    private val sort = savedStateHandle.getStateFlow(KEY_SORT, AlbumSortOrder.RecentlyAdded)
    private val viewMode = savedStateHandle.getStateFlow(KEY_VIEW_MODE, LibraryViewMode.Grid)
    private val filter: Flow<AlbumFilter> = combine(
        savedStateHandle.getStateFlow(KEY_STARRED, false),
        savedStateHandle.getStateFlow<String?>(KEY_GENRE, null),
        savedStateHandle.getStateFlow<Int?>(KEY_YEAR, null),
    ) { starred, genre, year -> AlbumFilter(starredOnly = starred, genre = genre, year = year) }
        .distinctUntilChanged()
    private val isRefreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<LibraryMessage?>(null)

    val albums: Flow<PagingData<Album>> = combine(sort, filter, ::Pair)
        .flatMapLatest { (sort, filter) -> albumRepository.albums(sort, filter) }
        .cachedIn(viewModelScope)

    val artists: Flow<PagingData<Artist>> = artistRepository.artists().cachedIn(viewModelScope)

    val songs: Flow<PagingData<Song>> = libraryRepository.songs().cachedIn(viewModelScope)

    private val settings = combine(tab, sort, viewMode, filter) { tab, sort, viewMode, filter ->
        Settings(tab, sort, viewMode, filter)
    }

    private val counts = combine(
        filter.flatMapLatest { albumRepository.albumCount(it) },
        artistRepository.artistCount,
        libraryRepository.songCount,
    ) { albums, artists, songs -> Counts(albums, artists, songs) }

    private val browse = combine(
        libraryRepository.genres,
        albumRepository.albumYears,
        playlistRepository.playlists,
    ) { genres, years, playlists -> Browse(genres, years, playlists) }

    private val status = combine(
        serverRepository.activeServer.map { it?.name },
        syncRepository.progress,
        isRefreshing,
        message,
    ) { serverName, progress, refreshing, message -> Status(serverName, progress, refreshing, message) }

    val uiState: StateFlow<LibraryUiState> = combine(settings, counts, browse, status) {
            settings,
            counts,
            browse,
            status,
        ->
        LibraryUiState(
            tab = settings.tab,
            sort = settings.sort,
            viewMode = settings.viewMode,
            filter = settings.filter,
            serverName = status.serverName,
            albumCount = counts.albums,
            artistCount = counts.artists,
            songCount = counts.songs,
            genres = browse.genres,
            years = browse.years,
            playlists = browse.playlists,
            syncProgress = status.progress,
            isRefreshing = status.refreshing,
            message = status.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun selectTab(tab: LibraryTab) {
        savedStateHandle[KEY_TAB] = tab
    }

    fun setSort(sort: AlbumSortOrder) {
        savedStateHandle[KEY_SORT] = sort
    }

    fun toggleViewMode() {
        val current = savedStateHandle.get<LibraryViewMode>(KEY_VIEW_MODE) ?: LibraryViewMode.Grid
        savedStateHandle[KEY_VIEW_MODE] =
            if (current == LibraryViewMode.Grid) LibraryViewMode.List else LibraryViewMode.Grid
    }

    fun toggleFavoritesFilter() {
        savedStateHandle[KEY_STARRED] = !(savedStateHandle.get<Boolean>(KEY_STARRED) ?: false)
    }

    fun setGenreFilter(genre: String?) {
        savedStateHandle[KEY_GENRE] = genre
    }

    fun setYearFilter(year: Int?) {
        savedStateHandle[KEY_YEAR] = year
    }

    fun clearFilters() {
        savedStateHandle[KEY_STARRED] = false
        savedStateHandle[KEY_GENRE] = null
        savedStateHandle[KEY_YEAR] = null
    }

    /** Schedules a library sync; progress shows through [LibraryUiState.syncProgress]. */
    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                syncRepository.requestSync()
            } catch (_: RemoteException) {
                message.value = LibraryMessage.RefreshFailed
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun onMessageDismiss() {
        message.value = null
    }

    /** Random songs from the whole library, played in the server's random order. */
    fun shuffleAll(playActions: PlayActions) {
        viewModelScope.launch {
            val songs = try {
                libraryRepository.randomSongs()
            } catch (_: RemoteException) {
                message.value = LibraryMessage.LoadFailed
                return@launch
            }
            if (songs.isEmpty()) {
                message.value = LibraryMessage.NothingToPlay
            } else {
                playActions.onPlaySongs(songs, 0, false)
            }
        }
    }

    /** Handles the album menu actions that need data; navigation actions stay with the screen. */
    fun onAlbumAction(
        album: Album,
        action: MediaAction,
        playActions: PlayActions,
        onAddToPlaylist: (List<String>) -> Unit,
    ) {
        when (action) {
            MediaAction.Play, MediaAction.Shuffle, MediaAction.PlayNext, MediaAction.AddToQueue,
            MediaAction.AddToPlaylist,
            -> viewModelScope.launch {
                val songs = loadSongs { albumRepository.songsForPlayback(album.id) } ?: return@launch
                dispatch(songs, action, playActions, onAddToPlaylist)
            }

            MediaAction.ShuffleArtistAlbums ->
                album.artistId?.let { playActions.onShuffleAlbums(AlbumShuffleSource.Artist(it)) }

            MediaAction.Favorite, MediaAction.Unfavorite ->
                setStarred(MediaKind.Album, album.id, starred = action == MediaAction.Favorite)

            else -> Unit
        }
    }

    /** Artists play their top songs; "Shuffle albums" shuffles every album of theirs. */
    fun onArtistAction(artist: Artist, action: MediaAction, playActions: PlayActions) {
        when (action) {
            MediaAction.Play, MediaAction.Shuffle -> viewModelScope.launch {
                val songs = loadSongs { artistRepository.topSongs(artist.id) } ?: return@launch
                playActions.onPlaySongs(songs, 0, action == MediaAction.Shuffle)
            }

            MediaAction.ShuffleAlbums -> playActions.onShuffleAlbums(AlbumShuffleSource.Artist(artist.id))

            MediaAction.Favorite, MediaAction.Unfavorite ->
                setStarred(MediaKind.Artist, artist.id, starred = action == MediaAction.Favorite)

            else -> Unit
        }
    }

    fun onSongAction(
        song: Song,
        action: MediaAction,
        playActions: PlayActions,
        onAddToPlaylist: (List<String>) -> Unit,
    ) {
        when (action) {
            MediaAction.PlayNext -> playActions.onPlayNext(listOf(song))

            MediaAction.AddToQueue -> playActions.onAddToQueue(listOf(song))

            MediaAction.InstantMix -> playActions.onStartInstantMix(song.id)

            MediaAction.AddToPlaylist -> onAddToPlaylist(listOf(song.id))

            MediaAction.Favorite, MediaAction.Unfavorite ->
                setStarred(MediaKind.Song, song.id, starred = action == MediaAction.Favorite)

            else -> Unit
        }
    }

    private suspend fun loadSongs(load: suspend () -> List<Song>): List<Song>? {
        val songs = try {
            load()
        } catch (_: RemoteException) {
            message.value = LibraryMessage.LoadFailed
            return null
        }
        if (songs.isEmpty()) message.value = LibraryMessage.NothingToPlay
        return songs.takeIf { it.isNotEmpty() }
    }

    private fun dispatch(
        songs: List<Song>,
        action: MediaAction,
        playActions: PlayActions,
        onAddToPlaylist: (List<String>) -> Unit,
    ) {
        when (action) {
            MediaAction.Play -> playActions.onPlaySongs(songs, 0, false)
            MediaAction.Shuffle -> playActions.onPlaySongs(songs, 0, true)
            MediaAction.PlayNext -> playActions.onPlayNext(songs)
            MediaAction.AddToQueue -> playActions.onAddToQueue(songs)
            MediaAction.AddToPlaylist -> onAddToPlaylist(songs.map { it.id })
            else -> Unit
        }
    }

    private fun setStarred(kind: MediaKind, id: String, starred: Boolean) {
        viewModelScope.launch {
            try {
                starRatingRepository.setStarred(kind, id, starred)
            } catch (_: RemoteException) {
                message.value = LibraryMessage.FavoriteFailed
            }
        }
    }

    private data class Settings(
        val tab: LibraryTab,
        val sort: AlbumSortOrder,
        val viewMode: LibraryViewMode,
        val filter: AlbumFilter,
    )

    private data class Counts(val albums: Int, val artists: Int, val songs: Int)

    private data class Browse(
        val genres: ImmutableList<Genre>,
        val years: ImmutableList<Int>,
        val playlists: ImmutableList<Playlist>,
    )

    private data class Status(
        val serverName: String?,
        val progress: SyncProgress,
        val refreshing: Boolean,
        val message: LibraryMessage?,
    )

    internal companion object {
        const val KEY_TAB = "tab"
        const val KEY_SORT = "sort"
        const val KEY_VIEW_MODE = "viewMode"
        const val KEY_STARRED = "starredOnly"
        const val KEY_GENRE = "genreFilter"
        const val KEY_YEAR = "yearFilter"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
