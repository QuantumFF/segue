package com.quantumff.segue.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.RemoteOutcome
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.OfflineLibraryView
import com.quantumff.segue.core.data.download.PinTarget
import com.quantumff.segue.core.data.getOrNull
import com.quantumff.segue.core.data.play.MediaActionHandler
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ArtistAlbums
import com.quantumff.segue.core.model.ArtistInfo
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.InstantMixSeed
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Artist detail (DESIGN.md §4.4). The artist and its albums come from Room; top songs and
 * `getArtistInfo2` are fetched on entry and are optional: their sections hide when they fail.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistRepository: ArtistRepository,
    private val starRatingRepository: StarRatingRepository,
    private val downloadRepository: DownloadRepository,
    offlineView: OfflineLibraryView,
    private val playIntents: PlayIntents,
    private val mediaActions: MediaActionHandler,
) : ViewModel() {
    private val artistId: String = checkNotNull(savedStateHandle[ARTIST_ID_KEY]) { "Missing $ARTIST_ID_KEY" }

    /** The last refresh's outcome; null while one runs. */
    private val refresh = MutableStateFlow<RemoteOutcome<Unit>?>(null)
    private val remote = MutableStateFlow(RemoteSections())
    private val message = MutableStateFlow<MediaActionFailure?>(null)
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<ArtistUiState> = combine(
        artistRepository.artist(artistId),
        artistRepository.artistAlbums(artistId),
        combine(remote, refresh, ::Pair),
        combine(message, busy, ::Pair),
        combine(downloadRepository.pinStatus(PinKind.Artist, artistId), offlineView.songAvailability, ::Pair),
    ) { artist, albums, (remote, refresh), (message, busy), (download, availability) ->
        when {
            artist != null -> {
                val (own, appearsOn) = ArtistAlbums.of(artistId, albums)
                ArtistUiState.Success(
                    artist = artist,
                    albums = own.toImmutableList(),
                    appearsOn = appearsOn.toImmutableList(),
                    topSongs = remote.topSongs,
                    similarArtists = remote.info?.similarArtists ?: persistentListOf(),
                    biography = remote.info?.biography,
                    trackCount = albums.sumOf { it.songCount },
                    isOffline = refresh?.showsOfflineChip == true,
                    isPreparingPlayback = busy,
                    message = message,
                    download = download,
                    availability = availability,
                )
            }

            refresh == null -> ArtistUiState.Loading

            refresh is RemoteOutcome.NotFound -> ArtistUiState.NotFound

            else -> ArtistUiState.Error
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ArtistUiState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        refresh.value = null
        viewModelScope.launch {
            launch {
                val info = remoteOutcome { artistRepository.artistInfo(artistId) }.getOrNull()
                remote.update { it.copy(info = info) }
            }
            refresh.value = remoteOutcome { artistRepository.refreshArtist(artistId) }
            // getTopSongs needs the artist's name, which the refresh stores on a first visit.
            val top = remoteOutcome { artistRepository.topSongs(artistId) }.getOrNull()
            remote.update { it.copy(topSongs = top ?: persistentListOf()) }
        }
    }

    /**
     * Plays the artist ([PlayTarget.Artist]): their top songs, or their albums when the server has
     * no top songs. [shuffle] shuffles the tracks.
     */
    fun play(shuffle: Boolean) = preparing {
        playIntents.play(PlayTarget.Artist(artistId), shuffle = shuffle, source = queueSource())
    }

    /** Plays the top song at [index] and the rest of the top songs after it. */
    fun playTopSong(index: Int) {
        val songs = remote.value.topSongs
        if (index !in songs.indices) return
        viewModelScope.launch { playIntents.play(PlayTarget.Songs(songs, index), source = queueSource()) }
    }

    /** The hero's "Shuffle albums": the floating Shuffle pill's tap. */
    fun shuffleAlbums() = playAlbums(AlbumOrder.Shuffled, QueuePlacement.Replace)

    /**
     * "Play albums" or "Shuffle albums" of the artist's albums, in the order the page shows them:
     * their own albums newest first, then the ones they appear on.
     */
    fun playAlbums(order: AlbumOrder, placement: QueuePlacement) {
        val request = AlbumPlayRequest(AlbumSource.Artist(artistId), order, placement)
        viewModelScope.launch { playIntents.playAlbums(request, queueSource()) }
    }

    /** Plays songs similar to the artist (`getSimilarSongs2`), topped up with their top songs. */
    fun startInstantMix() = preparing {
        val name = artistRepository.artist(artistId).first()?.name.orEmpty()
        playIntents.instantMix(InstantMixSeed.OfArtist(artistId, name))
    }

    /** The artist, as the queue source their plays record; unknown while the library does not have them. */
    private suspend fun queueSource(): QueueSource =
        artistRepository.artist(artistId).first()?.let { QueueSource.Artist(it.id, it.name) } ?: QueueSource.Unknown

    /** The artist's ⋮ menu ([MediaActionHandler]). */
    fun onArtistAction(action: MediaAction, host: MediaActionHost) {
        val artist = (uiState.value as? ArtistUiState.Success)?.artist ?: return
        preparing { mediaActions.artist(artist, action, host)?.let { message.value = it } }
    }

    /** A top song's ⋮ menu. */
    fun onSongAction(song: Song, action: MediaAction, host: MediaActionHost) {
        viewModelScope.launch { mediaActions.song(song, action, host)?.let { message.value = it } }
    }

    fun toggleArtistStar() {
        val artist = (uiState.value as? ArtistUiState.Success)?.artist ?: return
        setStarred(MediaKind.Artist, artist.id, !artist.isStarred)
    }

    /** Pins the artist: all of their albums, including ones added later. */
    fun download() {
        val artist = (uiState.value as? ArtistUiState.Success)?.artist ?: return
        viewModelScope.launch {
            val outcome = remoteOutcome {
                downloadRepository.pin(PinTarget.of(artist))
            }
            if (outcome.isFailure) message.value = MediaActionFailure.DownloadFailed
        }
    }

    fun removeDownload() {
        viewModelScope.launch { downloadRepository.unpin(PinKind.Artist, artistId) }
    }

    fun onMessageDismiss() {
        message.value = null
    }

    /** Runs [block] with the page showing that playback is being prepared. */
    private fun preparing(block: suspend () -> Unit) {
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }

    private fun setStarred(kind: MediaKind, id: String, starred: Boolean) {
        viewModelScope.launch {
            try {
                starRatingRepository.setStarred(kind, id, starred)
            } catch (_: RemoteException) {
                message.value = MediaActionFailure.FavoriteFailed
            }
        }
    }

    private data class RemoteSections(
        val topSongs: ImmutableList<Song> = persistentListOf(),
        val info: ArtistInfo? = null,
    )

    companion object {
        /** The [com.quantumff.segue.feature.artist.navigation.ArtistRoute] argument. */
        const val ARTIST_ID_KEY = "artistId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

sealed interface ArtistUiState {
    data object Loading : ArtistUiState

    data object Error : ArtistUiState

    data object NotFound : ArtistUiState

    data class Success(
        val artist: Artist,
        /** Albums credited to this artist as album artist, newest first. */
        val albums: ImmutableList<Album>,
        /** Albums by others that credit this artist (OpenSubsonic `artists`), newest first. */
        val appearsOn: ImmutableList<Album>,
        val topSongs: ImmutableList<Song>,
        val similarArtists: ImmutableList<Artist>,
        val biography: String?,
        val trackCount: Int,
        val isOffline: Boolean = false,
        /** Play, Shuffle or Instant mix is fetching songs. */
        val isPreparingPlayback: Boolean = false,
        /** A failed menu, Favorite or Download action, for the snackbar. */
        val message: MediaActionFailure? = null,
        /** The artist's pin status; null when it is not pinned for offline. */
        val download: DownloadStatus? = null,
        /** Row badges; offline, top songs that are not downloaded are dimmed and do not play. */
        val availability: SongAvailability = SongAvailability(),
    ) : ArtistUiState {
        val albumCount: Int get() = maxOf(artist.albumCount, albums.size)
    }
}
