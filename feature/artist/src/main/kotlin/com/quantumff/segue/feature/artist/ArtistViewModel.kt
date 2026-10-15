package com.quantumff.segue.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ArtistInfo
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val albumRepository: AlbumRepository,
    private val starRatingRepository: StarRatingRepository,
) : ViewModel() {
    private val artistId: String = checkNotNull(savedStateHandle[ARTIST_ID_KEY]) { "Missing $ARTIST_ID_KEY" }

    private val refresh = MutableStateFlow(RefreshState.Running)
    private val remote = MutableStateFlow(RemoteSections())
    private val message = MutableStateFlow<ArtistMessage?>(null)
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<ArtistUiState> = combine(
        artistRepository.artist(artistId),
        artistRepository.artistAlbums(artistId),
        remote,
        refresh,
        combine(message, busy, ::Pair),
    ) { artist, albums, remote, refresh, (message, busy) ->
        when {
            artist != null -> {
                val (own, appearsOn) = albums.partition { it.artistId == artistId }
                ArtistUiState.Success(
                    artist = artist,
                    albums = own.toImmutableList(),
                    appearsOn = appearsOn.toImmutableList(),
                    topSongs = remote.topSongs,
                    similarArtists = remote.info?.similarArtists ?: persistentListOf(),
                    biography = remote.info?.biography,
                    trackCount = albums.sumOf { it.songCount },
                    isOffline = refresh == RefreshState.Failed,
                    isPreparingPlayback = busy,
                    message = message,
                )
            }

            refresh == RefreshState.Running -> ArtistUiState.Loading

            refresh == RefreshState.NotFound -> ArtistUiState.NotFound

            else -> ArtistUiState.Error
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ArtistUiState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        refresh.value = RefreshState.Running
        viewModelScope.launch {
            launch {
                val info = runCatchingRemote { artistRepository.artistInfo(artistId) }
                remote.update { it.copy(info = info) }
            }
            refresh.value = try {
                artistRepository.refreshArtist(artistId)
                RefreshState.Done
            } catch (_: RemoteException.NotFound) {
                RefreshState.NotFound
            } catch (_: RemoteException) {
                RefreshState.Failed
            }
            // getTopSongs needs the artist's name, which the refresh stores on a first visit.
            val top = runCatchingRemote { artistRepository.topSongs(artistId) }
            remote.update { it.copy(topSongs = top ?: persistentListOf()) }
        }
    }

    /** Plays the top songs, or the artist's albums when the server has no top songs. */
    fun play(playActions: PlayActions, shuffle: Boolean) = withSongs { songs ->
        if (songs.isNotEmpty()) playActions.onPlaySongs(songs, 0, shuffle)
    }

    /** Plays the top song at [index] and the rest of the top songs after it. */
    fun playTopSong(playActions: PlayActions, index: Int) {
        val songs = remote.value.topSongs
        if (index in songs.indices) playActions.onPlaySongs(songs, index, false)
    }

    fun shuffleAlbums(playActions: PlayActions) = playActions.onShuffleAlbums(AlbumShuffleSource.Artist(artistId))

    /** Plays songs similar to the artist (`getSimilarSongs2`). */
    fun startInstantMix(playActions: PlayActions) {
        busy.value = true
        viewModelScope.launch {
            val songs = runCatchingRemote { artistRepository.similarSongs(artistId) }
            busy.value = false
            when {
                songs == null -> message.value = ArtistMessage.LoadFailed
                songs.isEmpty() -> message.value = ArtistMessage.NoSimilarSongs
                else -> playActions.onPlaySongs(songs, 0, false)
            }
        }
    }

    fun toggleArtistStar() {
        val artist = (uiState.value as? ArtistUiState.Success)?.artist ?: return
        setStarred(MediaKind.Artist, artist.id, !artist.isStarred)
    }

    fun toggleSongStar(song: Song) = setStarred(MediaKind.Song, song.id, !song.isStarred)

    fun onMessageDismiss() {
        message.value = null
    }

    private fun withSongs(block: (List<Song>) -> Unit) {
        val top = remote.value.topSongs
        if (top.isNotEmpty()) {
            block(top)
            return
        }
        val albums = (uiState.value as? ArtistUiState.Success)?.let { it.albums + it.appearsOn }.orEmpty()
        if (albums.isEmpty()) return
        busy.value = true
        viewModelScope.launch {
            val songs = runCatchingRemote { albums.flatMap { albumRepository.songsForPlayback(it.id) } }
            busy.value = false
            if (songs == null) message.value = ArtistMessage.LoadFailed else block(songs)
        }
    }

    private fun setStarred(kind: MediaKind, id: String, starred: Boolean) {
        viewModelScope.launch {
            try {
                starRatingRepository.setStarred(kind, id, starred)
            } catch (_: RemoteException) {
                message.value = ArtistMessage.FavoriteFailed
            }
        }
    }

    private inline fun <T> runCatchingRemote(block: () -> T): T? = try {
        block()
    } catch (_: RemoteException) {
        null
    }

    private data class RemoteSections(
        val topSongs: ImmutableList<Song> = persistentListOf(),
        val info: ArtistInfo? = null,
    )

    private enum class RefreshState { Running, Done, Failed, NotFound }

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
        /** Songs are being fetched for Play, Shuffle or Instant mix. */
        val isPreparingPlayback: Boolean = false,
        val message: ArtistMessage? = null,
    ) : ArtistUiState {
        val albumCount: Int get() = maxOf(artist.albumCount, albums.size)
    }
}

enum class ArtistMessage { FavoriteFailed, LoadFailed, NoSimilarSongs }
