package com.quantumff.segue.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.LibrarySyncRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.OfflineLibrary
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song
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

/** Transient messages shown in a snackbar. */
enum class HomeMessage {
    /** Could not load an album's songs to play it. */
    PlaybackLoadFailed,
    FavoriteFailed,
}

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
    /** The last refresh could not reach the server: cached data is shown with an offline chip. */
    val isOffline: Boolean = false,
    /** Offline (no network or offline mode): the carousels show downloaded albums and pinned playlists only. */
    val showsDownloadedOnly: Boolean = false,
    /** True until Room has answered, so the empty state does not flash on start. */
    val isLoading: Boolean = true,
    val message: HomeMessage? = null,
) {
    val isEmpty: Boolean
        get() = featured.isEmpty() && recentlyPlayed.isEmpty() && recentlyAdded.isEmpty() &&
            mostPlayed.isEmpty() && favorites.isEmpty() && playlists.isEmpty()

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

private data class Status(val isRefreshing: Boolean, val isOffline: Boolean, val message: HomeMessage?)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    libraryRepository: LibraryRepository,
    serverRepository: ServerRepository,
    private val syncRepository: LibrarySyncRepository,
    private val starRatingRepository: StarRatingRepository,
    private val offlineView: OfflineLibraryView,
) : ViewModel() {
    private val status = MutableStateFlow(Status(isRefreshing = false, isOffline = false, message = null))

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
        combine(status, pinnedFeatured, ::Pair),
    ) { (sections, downloaded), playlists, genres, (server, progress), (status, pinned) ->
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
            isOffline = status.isOffline,
            showsDownloadedOnly = downloaded != null,
            isLoading = false,
            message = status.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    init {
        refresh()
    }

    /** Refetches the server-ordered lists. Room keeps showing the cached ones meanwhile. */
    fun refresh() {
        if (status.value.isRefreshing) return
        // Offline the server is not asked: the cached, downloaded music is what there is.
        if (offlineView.isOffline.value) return
        status.update { it.copy(isRefreshing = true) }
        // A full refresh may reshuffle Featured again.
        pinnedFeatured.value = null
        viewModelScope.launch {
            val results = REFRESHED_LISTS.map { type ->
                async {
                    val size = if (type == AlbumListType.Random) RANDOM_SIZE else AlbumRepository.DEFAULT_LIST_SIZE
                    runCatchingRemote { albumRepository.refreshAlbumList(type, size) }
                }
            }.awaitAll()
            val offline = results.any { it is RemoteException.Unreachable }
            status.update { it.copy(isRefreshing = false, isOffline = offline) }
        }
    }

    /** The Explore header's refresh: a new random selection. */
    fun refreshExplore() {
        if (offlineView.isOffline.value) return
        uiState.value.featured.takeIf { it.isNotEmpty() }?.let { pinnedFeatured.value = it }
        viewModelScope.launch {
            runCatchingRemote { albumRepository.refreshAlbumList(AlbumListType.Random, RANDOM_SIZE) }
        }
    }

    fun retrySync() {
        viewModelScope.launch { runCatchingRemote { syncRepository.requestSync() } }
    }

    /** Loads [album]'s songs (fetching them if needed) and hands them to [onLoaded], usually a play intent. */
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

    /** Runs [block]; returns the [RemoteException] it threw, or null. */
    private suspend fun runCatchingRemote(block: suspend () -> Unit): RemoteException? = try {
        block()
        null
    } catch (e: RemoteException) {
        e
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
