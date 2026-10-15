package com.quantumff.segue.feature.nowplaying

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.LyricsRepository
import com.quantumff.segue.core.data.repository.SongFileInfoRepository
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongFileInfo
import com.quantumff.segue.core.playback.PlaybackController
import com.quantumff.segue.feature.nowplaying.lyrics.LyricSync
import com.quantumff.segue.feature.nowplaying.lyrics.LyricsContent
import com.quantumff.segue.feature.nowplaying.lyrics.PlaybackSample
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
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
enum class NowPlayingMessage {
    InstantMixFailed,
}

/**
 * Now Playing: the player state plus what the library and server know about the current track.
 * Lyrics and related songs load only while their panel is visible (see [setVisiblePanels]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val library: LibraryRepository,
    private val lyricsRepository: LyricsRepository,
    private val artists: ArtistRepository,
    private val fileInfo: SongFileInfoRepository,
) : ViewModel() {
    private val currentSongId: Flow<String?> = playback.eventState
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
        combine(playback.eventState, currentSong, currentFileInfo) { player, song, file ->
            player.toNowPlayingUiState(song, file)
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NowPlayingUiState())

    /**
     * Position in milliseconds. Collect it only where it is drawn, while visible: collecting it is
     * what runs the position ticker during playback.
     */
    val positionMillis: StateFlow<Long> = playback.state
        .map { it.positionMillis }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0L)

    private val visiblePanels = MutableStateFlow(emptySet<NowPlayingPanel>())
    private val retryLyrics = MutableStateFlow(0)
    private val retryRelated = MutableStateFlow(0)

    val lyrics: StateFlow<LyricsUiState> = combine(
        currentSongId,
        visiblePanels.map { NowPlayingPanel.Lyrics in it }.distinctUntilChanged(),
        retryLyrics,
    ) { id, visible, _ -> id.takeIf { visible } }
        .flatMapLatest { id -> if (id == null) flowOf(null) else loadLyrics(id) }
        // Keep showing the last lyrics while the panel is hidden, so reopening does not flash.
        .scanNotNull(LyricsUiState.Loading)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LyricsUiState.Loading)

    /** Index of the synced lyric line playing now, or -1. Runs only while synced lyrics are visible. */
    val activeLyricLine: StateFlow<Int> = combine(
        lyrics,
        visiblePanels.map { NowPlayingPanel.Lyrics in it }.distinctUntilChanged(),
    ) { state, visible -> ((state as? LyricsUiState.Loaded)?.content as? LyricsContent.Synced).takeIf { visible } }
        .distinctUntilChanged()
        .flatMapLatest { synced ->
            if (synced == null) {
                flowOf(-1)
            } else {
                val samples = playback.state.map { PlaybackSample(it.positionMillis, it.isPlaying) }
                LyricSync.activeLines(samples, synced.starts, synced.offsetMillis)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), -1)

    val related: StateFlow<RelatedUiState> = combine(
        currentSong.map { it?.artistId to (it != null) }.distinctUntilChanged(),
        visiblePanels.map { NowPlayingPanel.Related in it }.distinctUntilChanged(),
        retryRelated,
    ) { (artistId, known), visible, _ -> Triple(artistId, known, visible) }
        .flatMapLatest { (artistId, known, visible) ->
            when {
                !visible -> flowOf(null)
                artistId == null -> flowOf(if (known) RelatedUiState.Unavailable else RelatedUiState.Loading)
                else -> loadRelated(artistId)
            }
        }
        .scanNotNull(RelatedUiState.Loading)
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

    /** Off → Tracks → Albums → Off (DESIGN.md §4.7). */
    fun cycleShuffleMode() = playback.setShuffleMode(uiState.value.shuffleMode.cycled())

    /** Off → All → One → Off. */
    fun cycleRepeatMode() = playback.setRepeatMode(uiState.value.repeatMode.cycled())

    fun toggleFavorite() = playback.toggleFavorite()

    fun setSleepTimer(choice: SleepTimerChoice) = playback.setSleepTimer(choice.toSleepTimer(System.currentTimeMillis()))

    fun cancelSleepTimer() = playback.setSleepTimer(null)

    /** Replaces the queue with an instant mix seeded by the current track. */
    fun startInstantMix() {
        val songId = uiState.value.track?.songId ?: return
        viewModelScope.launch {
            val started = try {
                val seed = library.songsByIds(listOf(songId)).firstOrNull()
                seed != null && playback.playInstantMix(seed)
            } catch (e: RemoteException) {
                Log.w(TAG, "Instant mix failed", e)
                false
            }
            if (!started) messageChannel.send(NowPlayingMessage.InstantMixFailed)
        }
    }

    /** Plays the related songs from [index]. */
    fun playRelated(index: Int) {
        val songs = (related.value as? RelatedUiState.Loaded)?.songs ?: return
        playback.play(songs, startIndex = index, shuffle = false)
    }

    fun playNext(song: Song) = playback.playNext(listOf(song))

    fun addToQueue(songs: List<Song>) = playback.addToQueue(songs)

    private fun loadLyrics(songId: String): Flow<LyricsUiState> = flow {
        emit(LyricsUiState.Loading)
        val lyrics = loadOrNull("lyrics") { lyricsRepository.lyrics(songId) }
        emit(if (lyrics == null) LyricsUiState.Error else LyricsUiState.Loaded(LyricsContent.from(lyrics.firstOrNull())))
    }

    private fun loadRelated(artistId: String): Flow<RelatedUiState> = flow {
        emit(RelatedUiState.Loading)
        val songs = loadOrNull("related songs") { artists.similarSongs(artistId, RELATED_COUNT) }
        emit(if (songs == null) RelatedUiState.Error else RelatedUiState.Loaded(songs.toImmutableList()))
    }

    private suspend fun <T> loadOrNull(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RemoteException) {
        Log.w(TAG, "Could not load $what", e)
        null
    }

    private inline fun <reified E : Enum<E>> E.cycled(): E = enumValues<E>().let { it[(ordinal + 1) % it.size] }

    private companion object {
        const val TAG = "NowPlayingViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val RELATED_COUNT = 30
    }
}

/** Emits [initial], then every non-null upstream value: null means "keep the last value". */
private fun <T : Any> Flow<T?>.scanNotNull(initial: T): Flow<T> = flow {
    emit(initial)
    collect { if (it != null) emit(it) }
}.distinctUntilChanged()
