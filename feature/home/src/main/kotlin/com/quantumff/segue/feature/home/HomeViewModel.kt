package com.quantumff.segue.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.play.MediaActionHandler
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.LibrarySyncRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.data.snackbar.offline
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.OfflineLibrary
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.ScreenState
import com.quantumff.segue.core.model.SyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Home screen (DESIGN.md §4.1). Lists come from Room; the server refreshes them in the background. */
data class HomeUiState(
    val serverName: String? = null,
    /** 10 random albums for the feature carousel. */
    val featured: ImmutableList<Album> = persistentListOf(),
    val recentlyPlayed: ImmutableList<Album> = persistentListOf(),
    val recentlyAdded: ImmutableList<Album> = persistentListOf(),
    val mostPlayed: ImmutableList<Album> = persistentListOf(),
    /** The rest of the random list, under "Explore". */
    val explore: ImmutableList<Album> = persistentListOf(),
    val favorites: ImmutableList<Album> = persistentListOf(),
    val playlists: ImmutableList<Playlist> = persistentListOf(),
    val genres: ImmutableList<Genre> = persistentListOf(),
    val syncProgress: SyncProgress = SyncProgress.Idle,
    val isRefreshing: Boolean = false,
    /** Explore's own refresh is running: its header shows the inline spinner in place of the icon. */
    val isRefreshingExplore: Boolean = false,
    /**
     * Why Home shows less than the server has, for the chip: offline (no network or Offline mode) the
     * carousels show downloaded albums and pinned playlists only; online, the last refresh may not have
     * reached the server, so the cached library shows.
     */
    val offlineStatus: OfflineStatus = OfflineStatus.Online,
    /** The last refresh failed: the server could not be reached, or refused. */
    val refreshFailed: Boolean = false,
    /** True until Room has answered, so the empty state does not flash on start. */
    val isLoading: Boolean = true,
    /** A failed menu action, for the snackbar. */
    val message: MediaActionFailure? = null,
) {
    val isEmpty: Boolean
        get() = featured.isEmpty() && recentlyPlayed.isEmpty() && recentlyAdded.isEmpty() &&
            mostPlayed.isEmpty() && favorites.isEmpty() && playlists.isEmpty()

    /**
     * What Home shows (DESIGN.md §5.8). With nothing in Room, a refresh still running counts as
     * loading, so the empty state never flashes during the first refresh. Without data, offline
     * with nothing downloaded or after a failed refresh is an error with Retry, not an empty
     * library. A first sync filling an empty library shows as empty, with the sync's own copy.
     */
    val screenState: ScreenState
        get() = ScreenState.of(
            itemCount = when {
                isLoading -> null
                isEmpty -> 0
                else -> 1
            },
            isLoading = isRefreshing,
            failure = when {
                offlineStatus.isShowingDownloads -> ScreenState.Cause.Offline
                refreshFailed -> ScreenState.Cause.Server
                else -> null
            },
        )

    /** Explore's refresh needs the server, so its icon is disabled while Offline (GLOSSARY.md). */
    val canRefreshExplore: Boolean
        get() = !offlineStatus.isShowingDownloads

    /** The first sync is still filling the library. */
    val isSyncing: Boolean
        get() = syncProgress is SyncProgress.Running || syncProgress == SyncProgress.Pending
}

private data class AlbumSections(
    val random: List<Album>,
    val recent: List<Album>,
    val newest: List<Album>,
    val frequent: List<Album>,
    val favorites: List<Album>,
)

private data class Status(
    val isRefreshing: Boolean = false,
    val isRefreshingExplore: Boolean = false,
    /** The last refresh could not reach the server. */
    val serverUnreachable: Boolean = false,
    /** The last refresh failed, unreachable or refused. */
    val refreshFailed: Boolean = false,
    val message: MediaActionFailure? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    libraryRepository: LibraryRepository,
    serverRepository: ServerRepository,
    private val syncRepository: LibrarySyncRepository,
    private val offlineView: OfflineLibraryView,
    private val playIntents: PlayIntents,
    private val mediaActions: MediaActionHandler,
    private val snackbars: Snackbars,
) : ViewModel() {
    private val status = MutableStateFlow(Status())

    /** Featured albums held while Explore alone is refreshed, so the carousel does not reshuffle under the user. */
    private val pinnedFeatured = MutableStateFlow<List<Album>?>(null)

    private val albums = combine(
        albumRepository.albumList(AlbumListType.Random),
        albumRepository.albumList(AlbumListType.Recent),
        albumRepository.albumList(AlbumListType.Newest),
        albumRepository.albumList(AlbumListType.Frequent),
        albumRepository.starredAlbums,
    ) { random, recent, newest, frequent, favorites -> AlbumSections(random, recent, newest, frequent, favorites) }

    /** [albums], narrowed to what is downloaded while offline, paired with that downloaded music. */
    private val visibleAlbums = combine(albums, offlineView.downloadedWhileOffline) { sections, offline ->
        if (offline == null) return@combine sections to null
        fun List<Album>.downloaded() = OfflineLibrary.albums(this, offline)
        AlbumSections(
            random = sections.random.downloaded(),
            recent = sections.recent.downloaded(),
            newest = sections.newest.downloaded(),
            frequent = sections.frequent.downloaded(),
            favorites = sections.favorites.downloaded(),
        ) to offline
    }

    val uiState: StateFlow<HomeUiState> = combine(
        visibleAlbums,
        offlineView.playlists,
        libraryRepository.genres,
        combine(serverRepository.activeServer, syncRepository.progress, ::Pair),
        combine(status, pinnedFeatured, offlineView.offlineStatus, ::Triple),
    ) { (sections, downloaded), playlists, genres, (server, progress), (status, pinned, offlineStatus) ->
        // Featured albums held through an Explore refresh still follow the offline filter.
        val featured = pinned?.let { OfflineLibrary.albums(it, downloaded) } ?: sections.random.take(FEATURED_COUNT)
        val featuredIds = featured.mapTo(HashSet()) { it.id }
        HomeUiState(
            serverName = server?.name,
            featured = featured.toImmutableList(),
            recentlyPlayed = sections.recent.take(SECTION_SIZE).toImmutableList(),
            recentlyAdded = sections.newest.take(SECTION_SIZE).toImmutableList(),
            mostPlayed = sections.frequent.take(SECTION_SIZE).toImmutableList(),
            explore = sections.random.filterNot { it.id in featuredIds }.take(SECTION_SIZE).toImmutableList(),
            favorites = sections.favorites.take(SECTION_SIZE).toImmutableList(),
            playlists = playlists.take(SECTION_SIZE).toImmutableList(),
            genres = genres.sortedByDescending { it.albumCount }.take(GENRE_COUNT).toImmutableList(),
            syncProgress = progress,
            isRefreshing = status.isRefreshing,
            isRefreshingExplore = status.isRefreshingExplore,
            offlineStatus = offlineStatus.withServerUnreachable(status.serverUnreachable),
            refreshFailed = status.refreshFailed,
            isLoading = false,
            message = status.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    init {
        // Offline the server is not asked on entry: the downloaded music is what there is, and the chip says so.
        if (!offlineView.isOffline.value) refreshLists()
    }

    /**
     * Pull to refresh, and the chip's retry: refetches the server-ordered lists while Room keeps
     * showing the cached ones. Offline the server is not asked, and a snackbar says why.
     */
    fun refresh() {
        if (offlineView.isOffline.value) {
            snackbars.post(SnackbarText.offline)
        } else {
            refreshLists()
        }
    }

    private fun refreshLists() {
        if (status.value.isRefreshing) return
        status.update { it.copy(isRefreshing = true) }
        // A full refresh may reshuffle Featured again.
        pinnedFeatured.value = null
        viewModelScope.launch {
            val results = REFRESHED_LISTS.map { type ->
                async {
                    val size = if (type == AlbumListType.Random) RANDOM_SIZE else AlbumRepository.DEFAULT_LIST_SIZE
                    remoteOutcome { albumRepository.refreshAlbumList(type, size) }
                }
            }.awaitAll()
            val unreachable = results.any { it.showsOfflineChip }
            val failed = results.any { it.isFailure }
            status.update { it.copy(isRefreshing = false, serverUnreachable = unreachable, refreshFailed = failed) }
        }
    }

    /**
     * The Explore header's refresh: a new random selection, with the header's spinner while it
     * runs. Offline the icon is disabled; a failure leaves the lists as they were and says so.
     */
    fun refreshExplore() {
        if (offlineView.isOffline.value || status.value.isRefreshingExplore) return
        uiState.value.featured.takeIf { it.isNotEmpty() }?.let { pinnedFeatured.value = it }
        status.update { it.copy(isRefreshingExplore = true) }
        viewModelScope.launch {
            val outcome = remoteOutcome { albumRepository.refreshAlbumList(AlbumListType.Random, RANDOM_SIZE) }
            status.update { it.copy(isRefreshingExplore = false) }
            // Room keeps the old selection, so Featured and Explore stay as they were.
            if (outcome.isFailure) snackbars.post(SnackbarText.of(R.string.feature_home_explore_refresh_failed))
        }
    }

    fun retrySync() {
        viewModelScope.launch { remoteOutcome { syncRepository.requestSync() } }
    }

    /** A card's play button. */
    fun playAlbum(album: Album) {
        val source = QueueSource.Album(album.id, album.name)
        viewModelScope.launch { playIntents.play(PlayTarget.Album(album.id), source = source) }
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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val FEATURED_COUNT = 10
        const val SECTION_SIZE = 20
        const val GENRE_COUNT = 24

        /** Featured (10) plus Explore (20). */
        const val RANDOM_SIZE = FEATURED_COUNT + SECTION_SIZE
        val REFRESHED_LISTS = listOf(
            AlbumListType.Recent,
            AlbumListType.Newest,
            AlbumListType.Frequent,
            AlbumListType.Random,
        )
    }
}
