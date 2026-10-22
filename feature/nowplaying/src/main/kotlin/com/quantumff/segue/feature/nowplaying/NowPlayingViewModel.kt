package com.quantumff.segue.feature.nowplaying

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.exceptionOrNull
import com.quantumff.segue.core.data.getOrNull
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.play.PlayerStateSource
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.LyricsRepository
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.repository.SongFileInfoRepository
import com.quantumff.segue.core.model.CurrentSong
import com.quantumff.segue.core.model.InstantMixSeed
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongFileInfo
import com.quantumff.segue.core.model.currentSong
import com.quantumff.segue.core.playback.PlaybackController
import com.quantumff.segue.feature.nowplaying.lyrics.LyricSync
import com.quantumff.segue.feature.nowplaying.lyrics.LyricsContent
import com.quantumff.segue.feature.nowplaying.lyrics.PlaybackSample
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-off messages, shown as snackbars. */
sealed interface NowPlayingMessage {
    data class QueueSavedAsPlaylist(val name: String) : NowPlayingMessage

    data object QueueSaveFailed : NowPlayingMessage
}

/**
 * Now Playing: the player state plus what the library and server know about the current track.
 * Lyrics and related songs load only while their panel is visible (see [setVisiblePanels]).
 * It reads player state from [PlayerStateSource] and sends transport commands to [PlaybackController].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val player: PlayerStateSource,
    private val playback: PlaybackController,
    private val library: LibraryRepository,
    private val lyricsRepository: LyricsRepository,
    private val artists: ArtistRepository,
    private val fileInfo: SongFileInfoRepository,
    private val playIntents: PlayIntents,
    private val playlists: PlaylistRepository,
) : ViewModel() {
    private val currentSongId: Flow<String?> = player.state
        .map { it.currentItem?.songId }
        .distinctUntilChanged()

    private val currentSong: Flow<Song?> = currentSongId.flatMapLatest { id ->
        if (id == null) flowOf(null) else library.song(id)
    }

    private val currentFileInfo: Flow<SongFileInfo?> = currentSongId.flatMapLatest { id ->
        flow {
            emit(null)
            if (id != null) emit(loadOrNull("file info") { fileInfo.fileInfo(id) })
        }
    }

    val uiState: StateFlow<NowPlayingUiState> =
        combine(player.state, currentSong, currentFileInfo) { state, song, file ->
            state.toNowPlayingUiState(song, file)
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NowPlayingUiState())

    /**
     * Position in milliseconds. Collect it only where it is drawn, while visible: collecting it is
     * what runs the position ticker during playback.
     */
    val positionMillis: StateFlow<Long> = player.tickingState
        .map { it.positionMillis }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0L)

    private val visiblePanels = MutableStateFlow(emptySet<NowPlayingPanel>())
    private val retryLyrics = MutableStateFlow(0)
    private val retryRelated = MutableStateFlow(0)

    private fun visibility(panel: NowPlayingPanel): Flow<Boolean> =
        visiblePanels.map { panel in it }.distinctUntilChanged()

    val lyrics: StateFlow<LyricsUiState> = combine(
        currentSongId,
        visibility(NowPlayingPanel.Lyrics),
        retryLyrics,
    ) { id, visible, _ -> id.takeIf { visible } }
        .flatMapLatest { id -> if (id == null) flowOf(null) else loadLyrics(id) }
        // Keep showing the last lyrics while the panel is hidden, so reopening does not flash.
        .holdLastNonNull(LyricsUiState.Loading)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LyricsUiState.Loading)

    /** Index of the synced lyric line playing now, or -1. Runs only while synced lyrics are visible. */
    val activeLyricLine: StateFlow<Int> = combine(
        lyrics,
        visibility(NowPlayingPanel.Lyrics),
    ) { state, visible -> ((state as? LyricsUiState.Loaded)?.content as? LyricsContent.Synced).takeIf { visible } }
        .distinctUntilChanged()
        .flatMapLatest { synced ->
            if (synced == null) {
                flowOf(-1)
            } else {
                // The next-line timer runs only while the position moves, not while buffering.
                val samples = player.tickingState.map { state ->
                    PlaybackSample(state.positionMillis, isPlaying = state.isPlaying && !state.isBuffering)
                }
                LyricSync.activeLines(samples, synced.starts, synced.offsetMillis)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), -1)

    private val markedSong: Flow<CurrentSong?> = player.state.map { it.currentSong }.distinctUntilChanged()

    /** Songs similar to the current artist's, without the current song (see [shownWhile]). */
    val related: StateFlow<RelatedUiState> = combine(
        currentSong.map { it?.artistId to (it != null) }.distinctUntilChanged(),
        visibility(NowPlayingPanel.Related),
        retryRelated,
    ) { (artistId, known), visible, _ -> Triple(artistId, known, visible) }
        .flatMapLatest { (artistId, known, visible) ->
            when {
                !visible -> flowOf(null)
                artistId == null -> flowOf(if (known) RelatedUiState.Unavailable else RelatedUiState.Loading)
                else -> loadRelated(artistId)
            }
        }
        .holdLastNonNull(RelatedUiState.Loading)
        .combine(markedSong) { related, current -> related.shownWhile(current) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RelatedUiState.Loading)

    private val messageChannel = Channel<NowPlayingMessage>(Channel.BUFFERED)
    val messages: Flow<NowPlayingMessage> = messageChannel.receiveAsFlow()

    /** Which panels are on screen; lyrics and related songs load only for those. */
    fun setVisiblePanels(panels: Set<NowPlayingPanel>) {
        visiblePanels.value = panels
    }

    fun retryLyrics() = retryLyrics.value++

    fun retryRelated() = retryRelated.value++

    fun togglePlayPause() = playback.togglePlayPause()

    fun skipToNext() = playback.skipToNext()

    fun skipToPrevious() = playback.skipToPrevious()

    fun skipTo(timelineIndex: Int) = playback.skipTo(timelineIndex)

    fun seekTo(positionMillis: Long) = playback.seekTo(positionMillis)

    /** The playback error's Skip: plays the next item. */
    fun skipPastError() = playback.skipPastError()

    /** The playback error's Retry, for a network error: prepares the song again and plays it. */
    fun retryPlayback() = playback.retry()

    fun cycleRepeatMode() = playback.setRepeatMode(uiState.value.repeatMode.next())

    fun toggleFavorite() = playback.toggleFavorite()

    fun setSleepTimer(choice: SleepTimerChoice) =
        playback.setSleepTimer(choice.toSleepTimer(System.currentTimeMillis()))

    fun cancelSleepTimer() = playback.setSleepTimer(null)

    /**
     * Replaces the queue with an instant mix seeded by the current track. Like every play intent
     * ([PlayIntents]), it is refused offline and reports why it did not start.
     */
    fun startInstantMix() {
        val songId = uiState.value.track?.songId ?: return
        viewModelScope.launch { playIntents.instantMix(InstantMixSeed.OfLibrarySong(songId)) }
    }

    /** Plays the related songs from [index]. */
    fun playRelated(index: Int) {
        val songs = (related.value as? RelatedUiState.Loaded)?.songs ?: return
        viewModelScope.launch { playIntents.play(PlayTarget.Songs(songs, index)) }
    }

    fun playNext(song: Song) {
        viewModelScope.launch { playIntents.play(PlayTarget.Songs(listOf(song)), QueuePlacement.Next) }
    }

    fun addToQueue(songs: List<Song>) {
        viewModelScope.launch { playIntents.play(PlayTarget.Songs(songs), QueuePlacement.End) }
    }

    /** Saves the queue, in play order, as a new playlist called [name]. */
    fun saveQueueAsPlaylist(name: String) {
        val songIds = player.state.value.queue.map { it.songId }
        if (name.isBlank() || songIds.isEmpty()) return
        viewModelScope.launch {
            val message = try {
                playlists.createPlaylist(name.trim(), songIds)
                NowPlayingMessage.QueueSavedAsPlaylist(name.trim())
            } catch (_: RemoteException) {
                NowPlayingMessage.QueueSaveFailed
            }
            messageChannel.send(message)
        }
    }

    fun clearQueue() = playback.clearQueue()

    private fun loadLyrics(songId: String): Flow<LyricsUiState> = flow {
        emit(LyricsUiState.Loading)
        val lyrics = loadOrNull("lyrics") { lyricsRepository.lyrics(songId) }
        emit(
            if (lyrics ==
                null
            ) {
                LyricsUiState.Error
            } else {
                LyricsUiState.Loaded(LyricsContent.from(lyrics.firstOrNull()))
            },
        )
    }

    private fun loadRelated(artistId: String): Flow<RelatedUiState> = flow {
        emit(RelatedUiState.Loading)
        val songs = loadOrNull("related songs") { artists.similarSongs(artistId, RELATED_COUNT) }
        emit(if (songs == null) RelatedUiState.Error else RelatedUiState.Loaded(songs.toImmutableList()))
    }

    private inline fun <T> loadOrNull(what: String, block: () -> T): T? = remoteOutcome(block)
        .also { outcome -> outcome.exceptionOrNull()?.let { Log.w(TAG, "Could not load $what", it) } }
        .getOrNull()

    private companion object {
        const val TAG = "NowPlayingViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val RELATED_COUNT = 30
    }
}

/** Emits [initial], then every non-null upstream value: null means "keep the last value". */
private fun <T : Any> Flow<T?>.holdLastNonNull(initial: T): Flow<T> = flow {
    emit(initial)
    collect { if (it != null) emit(it) }
}.distinctUntilChanged()
