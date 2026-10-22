package com.quantumff.segue.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.play.MediaActionHandler
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.play.PlayerStateSource
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.LibrarySyncRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.GridSize
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.MediaActions
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.PlayingSong
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongDownload
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.model.playingSong
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
sealed interface LibraryMessage {
    /** A menu action failed. */
    data class ActionFailed(val failure: MediaActionFailure) : LibraryMessage

    data object RefreshFailed : LibraryMessage
}

/** An album, artist or song a Library tab lists: what a context menu is for, or what multi-select picks. */
sealed interface LibraryItem {
    val id: String

    data class OfAlbum(val album: Album) : LibraryItem {
        override val id: String get() = album.id
    }

    data class OfArtist(val artist: Artist) : LibraryItem {
        override val id: String get() = artist.id
    }

    data class OfSong(val song: Song) : LibraryItem {
        override val id: String get() = song.id
    }
}

/**
 * What multi-select has picked (DESIGN.md §4.2): items of one tab, in the order picked, which is
 * the order they are queued in. Never empty: dropping the last pick ends multi-select.
 */
sealed interface LibrarySelection {
    val ids: ImmutableSet<String>

    data class Albums(val albums: PersistentList<Album>) : LibrarySelection {
        override val ids: ImmutableSet<String> = albums.map { it.id }.toImmutableSet()
    }

    data class Artists(val artists: PersistentList<Artist>) : LibrarySelection {
        override val ids: ImmutableSet<String> = artists.map { it.id }.toImmutableSet()
    }

    data class Songs(val songs: PersistentList<Song>) : LibrarySelection {
        override val ids: ImmutableSet<String> = songs.map { it.id }.toImmutableSet()
    }

    companion object {
        /**
         * [current] with [item] picked, or dropped if it was; null once nothing is picked. An
         * item of another kind than [current]'s starts over with just that item.
         */
        fun toggle(current: LibrarySelection?, item: LibraryItem): LibrarySelection? = when (item) {
            is LibraryItem.OfAlbum ->
                Albums(((current as? Albums)?.albums ?: persistentListOf()).toggled(item.album) { it.id })

            is LibraryItem.OfArtist ->
                Artists(((current as? Artists)?.artists ?: persistentListOf()).toggled(item.artist) { it.id })

            is LibraryItem.OfSong ->
                Songs(((current as? Songs)?.songs ?: persistentListOf()).toggled(item.song) { it.id })
        }.takeIf { it.ids.isNotEmpty() }

        /** [current] with [item] picked or dropped, as [picked] says; null once nothing is picked. */
        fun set(current: LibrarySelection?, item: LibraryItem, picked: Boolean): LibrarySelection? =
            if ((current?.ids?.contains(item.id) == true) == picked) current else toggle(current, item)
    }
}

/** This list with [item] dropped if it is in it (by [id]), else added at the end. */
private fun <T> PersistentList<T>.toggled(item: T, id: (T) -> String): PersistentList<T> {
    val index = indexOfFirst { id(it) == id(item) }
    return if (index >= 0) removeAt(index) else add(item)
}

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.Albums,
    val sort: AlbumSortOrder = AlbumSortOrder.RecentlyAdded,
    val viewMode: LibraryViewMode = LibraryViewMode.Grid,
    /** Card size of the album and artist grids, which a pinch zooms. */
    val albumGridSize: GridSize = GridSize.Default,
    val artistGridSize: GridSize = GridSize.Default,
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
    /** Offline (no network or offline mode): tabs show downloaded music only. */
    val isOffline: Boolean = false,
    /** Pin status of pinned albums, artists and playlists, for card and row badges. */
    val pinnedAlbums: ImmutableMap<String, DownloadStatus> = persistentMapOf(),
    val pinnedArtists: ImmutableMap<String, DownloadStatus> = persistentMapOf(),
    val pinnedPlaylists: ImmutableMap<String, DownloadStatus> = persistentMapOf(),
    /** Downloads by song ID, for song row badges. */
    val songDownloads: ImmutableMap<String, SongDownload> = persistentMapOf(),
    /** Multi-select's picks; null when not selecting. */
    val selection: LibrarySelection? = null,
    /** The song that is playing, whose Songs tab rows are marked; null when nothing is current. */
    val playing: PlayingSong? = null,
) {
    /** Whether [song]'s row is marked as the current song. */
    fun isCurrent(song: Song): Boolean = playing?.songId == song.id

    /** The "Downloaded" chip's filter: chosen, or forced to downloaded only while offline. */
    val shownDownloadedFilter: Boolean? get() = if (isOffline) true else filter.downloaded

    /** The selection menu: Favorite and Download turn into their undo once every pick has them. */
    val selectionActions: ImmutableList<MediaAction>?
        get() = when (val selection = selection) {
            null -> null

            is LibrarySelection.Albums -> MediaActions.selection(
                MediaKind.Album,
                allStarred = selection.albums.all { it.isStarred },
                allDownloaded = selection.albums.all { it.id in pinnedAlbums },
            )

            is LibrarySelection.Artists -> MediaActions.selection(
                MediaKind.Artist,
                allStarred = selection.artists.all { it.isStarred },
                allDownloaded = selection.artists.all { it.id in pinnedArtists },
            )

            is LibrarySelection.Songs -> MediaActions.selection(
                MediaKind.Song,
                allStarred = selection.songs.all { it.isStarred },
                allDownloaded = false,
            )
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val libraryRepository: LibraryRepository,
    private val syncRepository: LibrarySyncRepository,
    serverRepository: ServerRepository,
    downloadRepository: DownloadRepository,
    private val offlineView: OfflineLibraryView,
    private val playIntents: PlayIntents,
    private val mediaActions: MediaActionHandler,
    private val preferences: UserPreferencesRepository,
    player: PlayerStateSource,
) : ViewModel() {

    private val tab = savedStateHandle.getStateFlow(KEY_TAB, LibraryTab.Albums)
    private val sort = savedStateHandle.getStateFlow(KEY_SORT, AlbumSortOrder.RecentlyAdded)
    private val viewMode = savedStateHandle.getStateFlow(KEY_VIEW_MODE, LibraryViewMode.Grid)
    private val starredFilter = savedStateHandle.getStateFlow<Boolean?>(KEY_STARRED, null)
    private val genreFilter = savedStateHandle.getStateFlow<String?>(KEY_GENRE, null)
    private val yearFilter = savedStateHandle.getStateFlow<Int?>(KEY_YEAR, null)
    private val downloadedFilter = savedStateHandle.getStateFlow<Boolean?>(KEY_DOWNLOADED, null)

    private val filter: Flow<AlbumFilter> =
        combine(starredFilter, genreFilter, yearFilter, downloadedFilter, ::AlbumFilter).distinctUntilChanged()

    /** [filter], limited to downloaded albums while offline: what the Albums tab shows. */
    private val effectiveFilter: Flow<AlbumFilter> = offlineView.albumFilter(filter)
    private val isRefreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<LibraryMessage?>(null)
    private val selection = MutableStateFlow<LibrarySelection?>(null)

    val albums: Flow<PagingData<Album>> = combine(sort, effectiveFilter, ::Pair)
        .flatMapLatest { (sort, filter) -> albumRepository.albums(sort, filter) }
        .cachedIn(viewModelScope)

    val artists: Flow<PagingData<Artist>> = offlineView.artists.cachedIn(viewModelScope)

    val songs: Flow<PagingData<Song>> = offlineView.songs.cachedIn(viewModelScope)

    private val gridSizes = preferences.preferences
        .map { it.albumGridSize to it.artistGridSize }
        .distinctUntilChanged()

    private val settings = combine(tab, sort, viewMode, filter, gridSizes) { tab, sort, viewMode, filter, sizes ->
        Settings(tab, sort, viewMode, filter, albumGridSize = sizes.first, artistGridSize = sizes.second)
    }

    private val counts = combine(
        effectiveFilter.flatMapLatest { albumRepository.albumCount(it) },
        offlineView.artistCount,
        offlineView.songCount,
    ) { albums, artists, songs -> Counts(albums, artists, songs) }

    private val downloads = combine(
        offlineView.isOffline,
        offlineView.pinStatuses,
        downloadRepository.songDownloads,
    ) { offline, pinned, songs ->
        Downloads(offline, pinned[PinKind.Album], pinned[PinKind.Artist], pinned[PinKind.Playlist], songs)
    }

    private val browse = combine(
        libraryRepository.genres,
        albumRepository.albumYears,
        offlineView.playlists,
    ) { genres, years, playlists -> Browse(genres, years, playlists) }

    private val status = combine(
        serverRepository.activeServer.map { it?.name },
        syncRepository.progress,
        isRefreshing,
        message,
        selection,
    ) { serverName, progress, refreshing, message, selection ->
        Status(serverName, progress, refreshing, message, selection)
    }

    private val playing: Flow<PlayingSong?> = player.state.map { it.playingSong() }.distinctUntilChanged()

    val uiState: StateFlow<LibraryUiState> = combine(
        settings,
        counts,
        browse,
        status,
        combine(downloads, playing, ::Pair),
    ) { settings, counts, browse, status, (downloads, playing) ->
        LibraryUiState(
            isOffline = downloads.isOffline,
            pinnedAlbums = downloads.albums,
            pinnedArtists = downloads.artists,
            pinnedPlaylists = downloads.playlists,
            songDownloads = downloads.songs,
            tab = settings.tab,
            sort = settings.sort,
            viewMode = settings.viewMode,
            albumGridSize = settings.albumGridSize,
            artistGridSize = settings.artistGridSize,
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
            selection = status.selection,
            playing = playing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    /** A tab's picks stay on it: another tab ends multi-select. */
    fun selectTab(tab: LibraryTab) {
        if (tab != this.tab.value) selection.value = null
        savedStateHandle[KEY_TAB] = tab
    }

    /** Starts multi-select with [item], or picks or drops it; dropping the last pick ends it. */
    fun toggleSelected(item: LibraryItem) {
        selection.update { LibrarySelection.toggle(it, item) }
    }

    /** A drag over [item] picks or drops it, as [picked] says (DESIGN.md §4.2). */
    fun setPicked(item: LibraryItem, picked: Boolean) {
        selection.update { LibrarySelection.set(it, item, picked) }
    }

    fun clearSelection() {
        selection.value = null
    }

    /** Runs a selection menu action on every pick, which ends multi-select. */
    fun onSelectionAction(action: MediaAction, host: MediaActionHost) {
        val selected = selection.getAndUpdate { null } ?: return
        menuAction {
            when (selected) {
                is LibrarySelection.Albums -> mediaActions.albums(selected.albums, action, host)
                is LibrarySelection.Artists -> mediaActions.artists(selected.artists, action, host)
                is LibrarySelection.Songs -> mediaActions.songs(selected.songs, action, host)
            }
        }
    }

    fun setSort(sort: AlbumSortOrder) {
        savedStateHandle[KEY_SORT] = sort
    }

    fun toggleViewMode() {
        val current = savedStateHandle.get<LibraryViewMode>(KEY_VIEW_MODE) ?: LibraryViewMode.Grid
        savedStateHandle[KEY_VIEW_MODE] =
            if (current == LibraryViewMode.Grid) LibraryViewMode.List else LibraryViewMode.Grid
    }

    /** A pinch on the [tab]'s grid set its card size; kept across launches. */
    fun setGridSize(tab: LibraryTab, size: GridSize) {
        viewModelScope.launch {
            preferences.update {
                when (tab) {
                    LibraryTab.Albums -> it.copy(albumGridSize = size)
                    LibraryTab.Artists -> it.copy(artistGridSize = size)
                    else -> it
                }
            }
        }
    }

    /** Favorites only, then everything but favorites, then off. */
    fun cycleFavoritesFilter() {
        savedStateHandle[KEY_STARRED] = nextChipFilter(savedStateHandle[KEY_STARRED])
    }

    /** Downloaded only, then everything not downloaded, then off. */
    fun cycleDownloadedFilter() {
        savedStateHandle[KEY_DOWNLOADED] = nextChipFilter(savedStateHandle[KEY_DOWNLOADED])
    }

    fun setGenreFilter(genre: String?) {
        savedStateHandle[KEY_GENRE] = genre
    }

    fun setYearFilter(year: Int?) {
        savedStateHandle[KEY_YEAR] = year
    }

    fun clearFilters() {
        savedStateHandle[KEY_STARRED] = null
        savedStateHandle[KEY_GENRE] = null
        savedStateHandle[KEY_YEAR] = null
        savedStateHandle[KEY_DOWNLOADED] = null
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

    /** Every song in the library, in a tracks shuffle; downloaded songs only while offline. */
    fun shuffleAll() {
        val request = AlbumPlayRequest(AlbumSource.Library, AlbumOrder.ShuffledTracks)
        viewModelScope.launch { playIntents.playAlbums(request) }
    }

    /**
     * "Play albums" ([AlbumOrder.InOrder]) or "Shuffle albums" of exactly what the Albums tab
     * shows: its sort and filters, limited to downloaded albums while offline.
     */
    fun playAlbums(order: AlbumOrder, placement: QueuePlacement) {
        val stored = AlbumFilter(starredFilter.value, genreFilter.value, yearFilter.value, downloadedFilter.value)
        val view = AlbumSource.Albums(sort.value, offlineView.albumFilter(stored))
        viewModelScope.launch { playIntents.playAlbums(AlbumPlayRequest(view, order, placement)) }
    }

    /** A genre's "Shuffle albums". */
    fun shuffleGenreAlbums(genre: String) {
        val request = AlbumPlayRequest(AlbumSource.Genre(genre), AlbumOrder.Shuffled)
        viewModelScope.launch { playIntents.playAlbums(request) }
    }

    /** A tap on a song row plays that song alone. */
    fun playSong(song: Song) {
        viewModelScope.launch { playIntents.play(PlayTarget.Songs(listOf(song))) }
    }

    fun onAlbumAction(album: Album, action: MediaAction, host: MediaActionHost) =
        menuAction { mediaActions.album(album, action, host) }

    fun onArtistAction(artist: Artist, action: MediaAction, host: MediaActionHost) =
        menuAction { mediaActions.artist(artist, action, host) }

    fun onSongAction(song: Song, action: MediaAction, host: MediaActionHost) =
        menuAction { mediaActions.song(song, action, host) }

    private fun menuAction(run: suspend () -> MediaActionFailure?) {
        viewModelScope.launch { run()?.let { message.value = LibraryMessage.ActionFailed(it) } }
    }

    private data class Settings(
        val tab: LibraryTab,
        val sort: AlbumSortOrder,
        val viewMode: LibraryViewMode,
        val filter: AlbumFilter,
        val albumGridSize: GridSize,
        val artistGridSize: GridSize,
    )

    private data class Counts(val albums: Int, val artists: Int?, val songs: Int)

    private data class Downloads(
        val isOffline: Boolean,
        val albums: ImmutableMap<String, DownloadStatus>,
        val artists: ImmutableMap<String, DownloadStatus>,
        val playlists: ImmutableMap<String, DownloadStatus>,
        val songs: ImmutableMap<String, SongDownload>,
    )

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
        val selection: LibrarySelection?,
    )

    internal companion object {
        const val KEY_TAB = "tab"
        const val KEY_SORT = "sort"
        const val KEY_VIEW_MODE = "viewMode"
        const val KEY_STARRED = "starredFilter"
        const val KEY_GENRE = "genreFilter"
        const val KEY_YEAR = "yearFilter"
        const val KEY_DOWNLOADED = "downloadedFilter"
        private const val STOP_TIMEOUT_MS = 5_000L

        /** A filter chip tap: off → only → excluded → off (true keeps matches, false drops them). */
        private fun nextChipFilter(current: Boolean?): Boolean? = when (current) {
            null -> true
            true -> false
            false -> null
        }
    }
}
