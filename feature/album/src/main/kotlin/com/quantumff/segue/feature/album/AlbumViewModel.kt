package com.quantumff.segue.feature.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.RemoteOutcome
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.download.PinTarget
import com.quantumff.segue.core.data.play.MediaActionHandler
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Album detail (DESIGN.md §4.3). Room is the source of truth; the album is refetched on entry. */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    artistRepository: ArtistRepository,
    private val starRatingRepository: StarRatingRepository,
    private val downloadRepository: DownloadRepository,
    offlineView: OfflineLibraryView,
    private val playIntents: PlayIntents,
    private val mediaActions: MediaActionHandler,
) : ViewModel() {
    private val albumId: String = checkNotNull(savedStateHandle[ALBUM_ID_KEY]) { "Missing $ALBUM_ID_KEY" }

    /** The last refresh's outcome; null while one runs. */
    private val refresh = MutableStateFlow<RemoteOutcome<Unit>?>(null)
    private val message = MutableStateFlow<AlbumMessage?>(null)

    private val album = albumRepository.album(albumId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val moreByArtist = album
        .map { it?.artistId }
        .distinctUntilChanged()
        .flatMapLatest { artistId ->
            if (artistId == null) flowOf(persistentListOf()) else artistRepository.artistAlbums(artistId)
        }
        .map { albums -> albums.filterNot { it.id == albumId }.toImmutableList() }

    val uiState: StateFlow<AlbumUiState> = combine(
        album,
        albumRepository.albumSongs(albumId),
        moreByArtist,
        combine(refresh, message, ::Pair),
        combine(downloadRepository.pinStatus(PinKind.Album, albumId), offlineView.songAvailability, ::Pair),
    ) { album, songs, more, (refresh, message), (download, availability) ->
        when {
            album != null -> AlbumUiState.Success(
                album = album,
                songs = songs,
                discs = groupDiscs(album, songs),
                moreByArtist = more,
                isOffline = refresh?.showsOfflineChip == true,
                message = message,
                download = download,
                availability = availability,
            )

            refresh == null -> AlbumUiState.Loading

            refresh is RemoteOutcome.NotFound -> AlbumUiState.NotFound

            else -> AlbumUiState.Error
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AlbumUiState.Loading)

    init {
        refresh()
    }

    /** Refetches the album; the cached copy stays on screen meanwhile. */
    fun refresh() {
        refresh.value = null
        viewModelScope.launch { refresh.value = remoteOutcome { albumRepository.refreshAlbum(albumId) } }
    }

    /** Plays the album from [startIndex]; [shuffle] shuffles its tracks. The album is the queue source. */
    fun play(startIndex: Int = 0, shuffle: Boolean = false) {
        val success = uiState.value as? AlbumUiState.Success ?: return
        val songs = success.songs
        if (songs.isEmpty()) return
        val target = PlayTarget.Songs(songs, startIndex.coerceIn(songs.indices))
        val source = QueueSource.Album(success.album.id, success.album.name)
        viewModelScope.launch { playIntents.play(target, shuffle = shuffle, source = source) }
    }

    /** Pins the album for offline use. */
    fun download() {
        val album = (uiState.value as? AlbumUiState.Success)?.album ?: return
        viewModelScope.launch {
            try {
                downloadRepository.pin(PinTarget.of(album))
            } catch (_: RemoteException) {
                message.value = AlbumMessage.ActionFailed(MediaActionFailure.DownloadFailed)
            }
        }
    }

    /** Unpins the album; files no other pin needs are deleted. */
    fun removeDownload() {
        viewModelScope.launch { downloadRepository.unpin(PinKind.Album, albumId) }
    }

    fun shuffleAlbums(option: ShuffleAlbumsOption) {
        val request = AlbumPlayRequest(option.source, AlbumOrder.Shuffled)
        viewModelScope.launch { playIntents.playAlbums(request, option.queueSource) }
    }

    /** The album's ⋮ menu ([MediaActionHandler]). */
    fun onAlbumAction(action: MediaAction, host: MediaActionHost) {
        val album = (uiState.value as? AlbumUiState.Success)?.album ?: return
        menuAction { mediaActions.album(album, action, host) }
    }

    /** A track's ⋮ menu. */
    fun onSongAction(song: Song, action: MediaAction, host: MediaActionHost) =
        menuAction { mediaActions.song(song, action, host) }

    private fun menuAction(run: suspend () -> MediaActionFailure?) {
        viewModelScope.launch { run()?.let { message.value = AlbumMessage.ActionFailed(it) } }
    }

    fun toggleAlbumStar() {
        val album = (uiState.value as? AlbumUiState.Success)?.album ?: return
        setStarred(MediaKind.Album, album.id, !album.isStarred)
    }

    fun setRating(rating: Int) {
        viewModelScope.launch {
            try {
                starRatingRepository.setRating(MediaKind.Album, albumId, rating)
            } catch (_: RemoteException) {
                message.value = AlbumMessage.RatingFailed
            }
        }
    }

    fun onMessageDismiss() {
        message.update { null }
    }

    private fun setStarred(kind: MediaKind, id: String, starred: Boolean) {
        viewModelScope.launch {
            try {
                starRatingRepository.setStarred(kind, id, starred)
            } catch (_: RemoteException) {
                message.value = AlbumMessage.ActionFailed(MediaActionFailure.FavoriteFailed)
            }
        }
    }

    companion object {
        /** The [com.quantumff.segue.feature.album.navigation.AlbumRoute] argument. */
        const val ALBUM_ID_KEY = "albumId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

sealed interface AlbumUiState {
    /** Nothing cached yet and the first fetch is running. */
    data object Loading : AlbumUiState

    /** Nothing cached and the fetch failed. */
    data object Error : AlbumUiState

    /** The server no longer has this album. */
    data object NotFound : AlbumUiState

    data class Success(
        val album: Album,
        /** Disc, then track order: the play order. */
        val songs: ImmutableList<Song>,
        val discs: ImmutableList<Disc>,
        val moreByArtist: ImmutableList<Album>,
        /** The refresh failed: cached data is shown with an offline chip. */
        val isOffline: Boolean = false,
        val message: AlbumMessage? = null,
        /** The album's pin status; null when it is not pinned for offline. */
        val download: DownloadStatus? = null,
        /** Row badges; offline, rows of songs that are not downloaded are dimmed and do not play. */
        val availability: SongAvailability = SongAvailability(),
    ) : AlbumUiState {
        val showDiscHeaders: Boolean get() = discs.size > 1
        val trackCount: Int get() = if (songs.isEmpty()) album.songCount else songs.size
        val durationSeconds: Long
            get() = if (songs.isEmpty()) album.durationSeconds.toLong() else songs.sumOf { it.durationSeconds.toLong() }

        /** "Shuffle albums" choices for the Shuffle long-press (DESIGN.md §4.3). */
        val shuffleOptions: ImmutableList<ShuffleAlbumsOption>
            get() = listOfNotNull(
                album.artistId?.let { ShuffleAlbumsOption.ByArtist(it, album.artist.orEmpty()) },
                album.genre?.takeIf { it.isNotBlank() }?.let { ShuffleAlbumsOption.ByGenre(it) },
            ).toImmutableList()
    }
}

/** One disc of the album. [title] is the OpenSubsonic disc subtitle, if any. */
data class Disc(val number: Int, val title: String?, val tracks: ImmutableList<IndexedSong>)

/** A song and its index in the album's play order. */
data class IndexedSong(val index: Int, val song: Song)

sealed interface ShuffleAlbumsOption {
    val source: AlbumSource

    /** What Now Playing says the shuffled albums came from. */
    val queueSource: QueueSource

    data class ByArtist(val artistId: String, val artistName: String) : ShuffleAlbumsOption {
        override val source get() = AlbumSource.Artist(artistId)
        override val queueSource get() = QueueSource.Artist(artistId, artistName)
    }

    data class ByGenre(val genre: String) : ShuffleAlbumsOption {
        override val source get() = AlbumSource.Genre(genre)
        override val queueSource get() = QueueSource.Genre(genre)
    }
}

sealed interface AlbumMessage {
    /** A menu action, or the header's Favorite or Download, failed. */
    data class ActionFailed(val failure: MediaActionFailure) : AlbumMessage

    data object RatingFailed : AlbumMessage
}

/** Groups [songs] (already in disc and track order) by disc number. Songs without one go on disc 1. */
internal fun groupDiscs(album: Album, songs: List<Song>): ImmutableList<Disc> {
    val titles = album.discTitles.associate { it.disc to it.title }
    return songs.withIndex()
        .groupBy { it.value.discNumber ?: 1 }
        .map { (number, entries) ->
            Disc(
                number = number,
                title = titles[number]?.takeIf { it.isNotBlank() },
                tracks = entries.map { IndexedSong(it.index, it.value) }.toImmutableList(),
            )
        }
        .toImmutableList()
}
