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
import com.quantumff.segue.core.model.ArtistFilter
import com.quantumff.segue.core.model.ArtistSortOrder
import com.quantumff.segue.core.model.CurrentSong
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.GridSize
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.MediaActions
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.ScreenState
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongDownload
import com.quantumff.segue.core.model.SongFilter
import com.quantumff.segue.core.model.SongSortOrder
import com.quantumff.segue.core.model.SyncProgress
import com.quantumff.segue.core.model.currentSong
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

/**
 * A tab's Favorites and Downloaded chips (DESIGN.md §4.2), shared by every tab that has them. Each
 * is off (null), on (true: only matches) or inverted (false: everything but matches).
 */
data class ChipFilters(val favorites: Boolean?, val downloaded: Boolean?, val isOffline: Boolean) {
    /** The Downloaded chip as shown: offline holds it on, since only downloaded music plays. */
    val shownDownloaded: Boolean? get() = if (isOffline) true else downloaded
}

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.Albums,
    val viewMode: LibraryViewMode = LibraryViewMode.Grid,
    /** Card size of the album and artist grids, which a pinch zooms. */
    val albumGridSize: GridSize = GridSize.Default,
    val artistGridSize: GridSize = GridSize.Default,
    // Each tab keeps its own sort and filters.
    val albumSort: AlbumSortOrder = AlbumSortOrder.RecentlyAdded,
    val albumFilter: AlbumFilter = AlbumFilter(),
    val artistSort: ArtistSortOrder = ArtistSortOrder.Name,
    val artistFilter: ArtistFilter = ArtistFilter(),
    val songSort: SongSortOrder = SongSortOrder.Title,
    val songFilter: SongFilter = SongFilter(),
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
    val currentSong: CurrentSong? = null,
) {
    /**
     * What [tab] shows (DESIGN.md §5.8). With nothing in Room, a running sync is loading and a
     * failed one is an error with Retry, not an empty library. Offline, or with filters that
     * match nothing, an empty tab is just empty: the library may well have data.
     */
    fun screenState(tab: LibraryTab): ScreenState = when (tab) {
        LibraryTab.Albums -> syncedState(albumCount, filtered = albumFilter.isActive)
        LibraryTab.Artists -> syncedState(artistCount, filtered = artistFilter.isActive)
        LibraryTab.Songs -> syncedState(songCount, filtered = songFilter.isActive)
        LibraryTab.Genres -> syncedState(genres.size.takeIf { albumCount != null })
        LibraryTab.Playlists -> ScreenState.of(playlists?.size)
        LibraryTab.Downloads -> ScreenState.Content
    }

    /** A tab filled by the library sync, holding [count] items. */
    private fun syncedState(count: Int?, filtered: Boolean = false): ScreenState {
        val followsSync = !isOffline && !filtered
        return ScreenState.of(
            itemCount = count,
            isLoading = followsSync && (syncProgress is SyncProgress.Pending || syncProgress is SyncProgress.Running),
            failure = ScreenState.Cause.Server.takeIf { followsSync && syncProgress is SyncProgress.Failed },
        )
    }

    /** Whether [song]'s row is marked as the current song. */
    fun isCurrent(song: Song): Boolean = currentSong?.isOf(song) == true

    /** Shuffle all shows only once the Songs tab is known to have songs, so it never flashes. */
    val showsShuffleAll: Boolean get() = (songCount ?: 0) > 0

    /** The [tab]'s Favorites and Downloaded chips; null for a tab without them. */
    fun chipFilters(tab: LibraryTab): ChipFilters? = when (tab) {
        LibraryTab.Albums -> ChipFilters(albumFilter.starred, albumFilter.downloaded, isOffline)
        LibraryTab.Artists -> ChipFilters(artistFilter.starred, artistFilter.downloaded, isOffline)
        LibraryTab.Songs -> ChipFilters(songFilter.starred, songFilter.downloaded, isOffline)
        LibraryTab.Playlists, LibraryTab.Genres, LibraryTab.Downloads -> null
    }

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
    private val viewMode = savedStateHandle.getStateFlow(KEY_VIEW_MODE, LibraryViewMode.Grid)

    private val albumSort = savedStateHandle.getStateFlow(KEY_ALBUM_SORT, AlbumSortOrder.RecentlyAdded)
    private val albumChips = TabChipFilters(savedStateHandle, LibraryTab.Albums)
    private val genreFilter = savedStateHandle.getStateFlow<String?>(KEY_GENRE, null)
    private val yearFilter = savedStateHandle.getStateFlow<Int?>(KEY_YEAR, null)

    private val albumFilter: Flow<AlbumFilter> =
        combine(albumChips.favorites, genreFilter, yearFilter, albumChips.downloaded, ::AlbumFilter)
            .distinctUntilChanged()

    /** [albumFilter], limited to downloaded albums while offline: what the Albums tab shows. */
    private val effectiveAlbumFilter: Flow<AlbumFilter> = offlineView.albumFilter(albumFilter)

    private val artistSort = savedStateHandle.getStateFlow(KEY_ARTIST_SORT, ArtistSortOrder.Name)
    private val artistChips = TabChipFilters(savedStateHandle, LibraryTab.Artists)
    private val artistFilter: Flow<ArtistFilter> =
        combine(artistChips.favorites, artistChips.downloaded, ::ArtistFilter).distinctUntilChanged()

    private val songSort = savedStateHandle.getStateFlow(KEY_SONG_SORT, SongSortOrder.Title)
    private val songChips = TabChipFilters(savedStateHandle, LibraryTab.Songs)
    private val songFilter: Flow<SongFilter> =
        combine(songChips.favorites, songChips.downloaded, ::SongFilter).distinctUntilChanged()

    /** The chips of each tab that has them; a tab's chips act on that tab only. */
    private val chips = mapOf(
        LibraryTab.Albums to albumChips,
        LibraryTab.Artists to artistChips,
        LibraryTab.Songs to songChips,
    )
    private val isRefreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<LibraryMessage?>(null)
    private val selection = MutableStateFlow<LibrarySelection?>(null)

    val albums: Flow<PagingData<Album>> = combine(albumSort, effectiveAlbumFilter, ::Pair)
        .flatMapLatest { (sort, filter) -> albumRepository.albums(sort, filter) }
        .cachedIn(viewModelScope)

    /** The Artists tab, sorted and filtered; offline only artists of downloaded music. */
    val artists: Flow<PagingData<Artist>> = combine(artistSort, artistFilter, ::Pair)
        .flatMapLatest { (sort, filter) -> offlineView.artists(sort, filter) }
        .cachedIn(viewModelScope)

    /** The Songs tab, sorted and filtered; offline only downloaded songs. */
    val songs: Flow<PagingData<Song>> = combine(songSort, songFilter, ::Pair)
        .flatMapLatest { (sort, filter) -> offlineView.songs(sort, filter) }
        .cachedIn(viewModelScope)

    private val gridSizes = preferences.preferences
        .map { it.albumGridSize to it.artistGridSize }
        .distinctUntilChanged()

    /** Each tab's sort and filters. A tab that gains a sort adds its pair here. */
    private val tabViews = combine(
        albumSort,
        albumFilter,
        artistSort,
        artistFilter,
        combine(songSort, songFilter, ::Pair),
    ) { albumSort, albumFilter, artistSort, artistFilter, (songSort, songFilter) ->
        TabViews(albumSort, albumFilter, artistSort, artistFilter, songSort, songFilter)
    }

    private val settings = combine(tab, viewMode, gridSizes, tabViews) { tab, viewMode, sizes, views ->
        Settings(tab, viewMode, views, albumGridSize = sizes.first, artistGridSize = sizes.second)
    }

    private val counts = combine(
        effectiveAlbumFilter.flatMapLatest { albumRepository.albumCount(it) },
        artistFilter.flatMapLatest { offlineView.artistCount(it) },
        songFilter.flatMapLatest { offlineView.songCount(it) },
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

    private val currentSong: Flow<CurrentSong?> = player.state.map { it.currentSong }.distinctUntilChanged()

    val uiState: StateFlow<LibraryUiState> = combine(
        settings,
        counts,
        browse,
        status,
        combine(downloads, currentSong, ::Pair),
    ) { settings, counts, browse, status, (downloads, currentSong) ->
        LibraryUiState(
            isOffline = downloads.isOffline,
            pinnedAlbums = downloads.albums,
            pinnedArtists = downloads.artists,
            pinnedPlaylists = downloads.playlists,
            songDownloads = downloads.songs,
            tab = settings.tab,
            viewMode = settings.viewMode,
            albumGridSize = settings.albumGridSize,
            artistGridSize = settings.artistGridSize,
            albumSort = settings.views.albumSort,
            albumFilter = settings.views.albumFilter,
            artistSort = settings.views.artistSort,
            artistFilter = settings.views.artistFilter,
            songSort = settings.views.songSort,
            songFilter = settings.views.songFilter,
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
            currentSong = currentSong,
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

    fun setAlbumSort(sort: AlbumSortOrder) {
        savedStateHandle[KEY_ALBUM_SORT] = sort
    }

    fun setArtistSort(sort: ArtistSortOrder) {
        savedStateHandle[KEY_ARTIST_SORT] = sort
    }

    fun setSongSort(sort: SongSortOrder) {
        savedStateHandle[KEY_SONG_SORT] = sort
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

    /** The [tab]'s Favorites chip: favorites only, then everything but favorites, then off. */
    fun cycleFavoritesFilter(tab: LibraryTab) {
        chips[tab]?.cycleFavorites()
    }

    /** The [tab]'s Downloaded chip: downloaded only, then everything not downloaded, then off. */
    fun cycleDownloadedFilter(tab: LibraryTab) {
        chips[tab]?.cycleDownloaded()
    }

    fun setGenreFilter(genre: String?) {
        savedStateHandle[KEY_GENRE] = genre
    }

    fun setYearFilter(year: Int?) {
        savedStateHandle[KEY_YEAR] = year
    }

    /** Turns off every filter of [tab]; other tabs keep theirs. */
    fun clearFilters(tab: LibraryTab) {
        chips[tab]?.clear()
        if (tab == LibraryTab.Albums) {
            savedStateHandle[KEY_GENRE] = null
            savedStateHandle[KEY_YEAR] = null
        }
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

    /**
     * Every song in the library, in a tracks shuffle. Offline the downloaded songs, read from Room,
     * since nothing else can play.
     */
    fun shuffleAll() {
        val source = QueueSource.Library(shuffled = true)
        viewModelScope.launch {
            if (offlineView.isOffline.value) {
                val songs = libraryRepository.allDownloadedSongs()
                playIntents.play(PlayTarget.Songs(songs), shuffle = true, source = source)
            } else {
                playIntents.playAlbums(AlbumPlayRequest(AlbumSource.Library, AlbumOrder.ShuffledTracks), source)
            }
        }
    }

    /**
     * "Play albums" ([AlbumOrder.InOrder]) or "Shuffle albums" of exactly what the Albums tab
     * shows: its sort and filters, limited to downloaded albums while offline.
     */
    fun playAlbums(order: AlbumOrder, placement: QueuePlacement) {
        val stored = AlbumFilter(
            albumChips.favorites.value,
            genreFilter.value,
            yearFilter.value,
            albumChips.downloaded.value,
        )
        val view = AlbumSource.Albums(albumSort.value, offlineView.albumFilter(stored))
        val source = QueueSource.Library(shuffled = order != AlbumOrder.InOrder)
        viewModelScope.launch { playIntents.playAlbums(AlbumPlayRequest(view, order, placement), source) }
    }

    /** A genre's "Shuffle albums". */
    fun shuffleGenreAlbums(genre: String) {
        val request = AlbumPlayRequest(AlbumSource.Genre(genre), AlbumOrder.Shuffled)
        viewModelScope.launch { playIntents.playAlbums(request, QueueSource.Genre(genre)) }
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

    private data class TabViews(
        val albumSort: AlbumSortOrder,
        val albumFilter: AlbumFilter,
        val artistSort: ArtistSortOrder,
        val artistFilter: ArtistFilter,
        val songSort: SongSortOrder,
        val songFilter: SongFilter,
    )

    private data class Settings(
        val tab: LibraryTab,
        val viewMode: LibraryViewMode,
        val views: TabViews,
        val albumGridSize: GridSize,
        val artistGridSize: GridSize,
    )

    private data class Counts(val albums: Int, val artists: Int, val songs: Int)

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
        const val KEY_VIEW_MODE = "viewMode"
        const val KEY_ALBUM_SORT = "albumSort"
        const val KEY_ARTIST_SORT = "artistSort"
        const val KEY_SONG_SORT = "songSort"
        const val KEY_GENRE = "genreFilter"
        const val KEY_YEAR = "yearFilter"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * One tab's Favorites and Downloaded chips ([ChipFilters]), kept in saved state under the tab's
 * own keys so each tab keeps its own.
 */
private class TabChipFilters(private val savedState: SavedStateHandle, tab: LibraryTab) {
    private val favoritesKey = "${tab.name}.favoritesFilter"
    private val downloadedKey = "${tab.name}.downloadedFilter"

    val favorites: StateFlow<Boolean?> = savedState.getStateFlow(favoritesKey, null)
    val downloaded: StateFlow<Boolean?> = savedState.getStateFlow(downloadedKey, null)

    fun cycleFavorites() {
        savedState[favoritesKey] = nextChipFilter(favorites.value)
    }

    fun cycleDownloaded() {
        savedState[downloadedKey] = nextChipFilter(downloaded.value)
    }

    fun clear() {
        savedState[favoritesKey] = null
        savedState[downloadedKey] = null
    }

    /** A chip tap: off → only → excluded → off (true keeps matches, false drops them). */
    private fun nextChipFilter(current: Boolean?): Boolean? = when (current) {
        null -> true
        true -> false
        false -> null
    }
}
