package com.quantumff.segue.feature.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.OfflineModeRepository
import com.quantumff.segue.core.data.download.PinTarget
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongDownload
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
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
    offlineModeRepository: OfflineModeRepository,
) : ViewModel() {
    private val albumId: String = checkNotNull(savedStateHandle[ALBUM_ID_KEY]) { "Missing $ALBUM_ID_KEY" }

    private val refresh = MutableStateFlow(RefreshState.Running)
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

    private val offline = combine(
        downloadRepository.pinStatus(PinKind.Album, albumId),
        downloadRepository.songDownloads,
        offlineModeRepository.isOffline,
        ::OfflineState,
    )

    val uiState: StateFlow<AlbumUiState> = combine(
        album,
        albumRepository.albumSongs(albumId),
        moreByArtist,
        combine(refresh, message, ::Pair),
        offline,
    ) { album, songs, more, (refresh, message), offline ->
        when {
            album != null -> AlbumUiState.Success(
                album = album,
                songs = songs,
                discs = groupDiscs(album, songs),
                moreByArtist = more,
                isOffline = refresh == RefreshState.Failed,
                message = message,
                download = offline.pin,
                songDownloads = offline.songs,
                isOfflineMode = offline.isOffline,
            )

            refresh == RefreshState.Running -> AlbumUiState.Loading

            refresh == RefreshState.NotFound -> AlbumUiState.NotFound

            else -> AlbumUiState.Error
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AlbumUiState.Loading)

    init {
        refresh()
    }

    /** Refetches the album; the cached copy stays on screen meanwhile. */
    fun refresh() {
        refresh.value = RefreshState.Running
        viewModelScope.launch {
            refresh.value = try {
                albumRepository.refreshAlbum(albumId)
                RefreshState.Done
            } catch (_: RemoteException.NotFound) {
                RefreshState.NotFound
            } catch (_: RemoteException) {
                RefreshState.Failed
            }
        }
    }

    /** Plays the album from [startIndex]; [shuffle] shuffles its tracks. */
    fun play(playActions: PlayActions, startIndex: Int = 0, shuffle: Boolean = false) {
        val songs = (uiState.value as? AlbumUiState.Success)?.songs ?: return
        if (songs.isEmpty()) return
        playActions.onPlaySongs(songs, startIndex.coerceIn(songs.indices), shuffle)
    }

    /** Pins the album for offline use. */
    fun download() {
        val album = (uiState.value as? AlbumUiState.Success)?.album ?: return
        viewModelScope.launch {
            try {
                downloadRepository.pin(PinTarget(PinKind.Album, album.id, album.name, album.coverArtId))
            } catch (_: RemoteException) {
                message.value = AlbumMessage.DownloadFailed
            }
        }
    }

    /** Unpins the album; files no other pin needs are deleted. */
    fun removeDownload() {
        viewModelScope.launch { downloadRepository.unpin(PinKind.Album, albumId) }
    }

    /** Pins one song for offline use. */
    fun downloadSong(song: Song) {
        viewModelScope.launch {
            try {
                downloadRepository.pin(PinTarget(PinKind.Song, song.id, song.title, song.coverArtId))
            } catch (_: RemoteException) {
                message.value = AlbumMessage.DownloadFailed
            }
        }
    }

    fun shuffleAlbums(playActions: PlayActions, option: ShuffleAlbumsOption) {
        playActions.shuffleAlbums(option.source)
    }

    fun toggleAlbumStar() {
        val album = (uiState.value as? AlbumUiState.Success)?.album ?: return
        setStarred(MediaKind.Album, album.id, !album.isStarred)
    }

    fun toggleSongStar(song: Song) = setStarred(MediaKind.Song, song.id, !song.isStarred)

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
                message.value = AlbumMessage.FavoriteFailed
            }
        }
    }

    private enum class RefreshState { Running, Done, Failed, NotFound }

    private data class OfflineState(
        val pin: DownloadStatus?,
        val songs: ImmutableMap<String, SongDownload>,
        val isOffline: Boolean,
    )

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
        /** Downloads by song ID, for row badges. */
        val songDownloads: ImmutableMap<String, SongDownload> = persistentMapOf(),
        /** Offline mode: rows of songs that are not downloaded are dimmed and do not play. */
        val isOfflineMode: Boolean = false,
    ) : AlbumUiState {
        fun isAvailable(song: Song): Boolean =
            !isOfflineMode || songDownloads[song.id]?.state == DownloadState.Completed

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

    data class ByArtist(val artistId: String, val artistName: String) : ShuffleAlbumsOption {
        override val source get() = AlbumSource.Artist(artistId)
    }

    data class ByGenre(val genre: String) : ShuffleAlbumsOption {
        override val source get() = AlbumSource.Genre(genre)
    }
}

enum class AlbumMessage { FavoriteFailed, RatingFailed, DownloadFailed }

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
