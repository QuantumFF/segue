package com.quantumff.segue.feature.nowplaying

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.OfflineLibraryView
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
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

/**
 * Now Playing: the player state plus what the library and server know about the current track.
 * Lyrics and related songs load only while their panel is visible (see [setVisiblePanels]).
 * It reads player state from [PlayerStateSource] and sends transport commands to [PlaybackController].
 * Its messages go to the app's [Snackbars], which the shell shows over Now Playing.
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
    private val snackbars: Snackbars,
    private val offlineView: OfflineLibraryView,
    private val preferences: UserPreferencesRepository,
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

    /** The details sheet for any library song, such as a queue row's Info: the song and its file. */
    fun songDetails(songId: String): Flow<TrackDetails?> = combine(
        library.song(songId),
        flow {
            emit(null)
            emit(loadOrNull("file info") { fileInfo.fileInfo(songId) })
        },
    ) { song, file -> song?.toDetails(file) }

    // The speed is the player's. A speed just chosen ([setPlaybackSpeed]) shows at once, until the
    // player next reports its speed, so the sheet and badge don't wait for the playback service.
    private val chosenSpeed = MutableStateFlow<Float?>(null)
    private var chosenSpeedJob: Job? = null

    val uiState: StateFlow<NowPlayingUiState> =
        combine(player.state, currentSong, currentFileInfo, chosenSpeed) { state, song, file, chosen ->
            val ui = state.toNowPlayingUiState(song, file)
            if (chosen == null) ui else ui.copy(speed = chosen)
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

    private val sleepTimerConfirmations = SleepTimerConfirmations(snackbars)

    private val visiblePanels = MutableStateFlow(emptySet<NowPlayingPanel>())
    private val retryLyrics = MutableStateFlow(0)
    private val retryRelated = MutableStateFlow(0)

    private fun visibility(panel: NowPlayingPanel): Flow<Boolean> =
        visiblePanels.map { panel in it }.distinctUntilChanged()

    // Lyrics and related songs reload when the device goes offline or back online.
    val lyrics: StateFlow<LyricsUiState> = combine(
        currentSongId,
        visibility(NowPlayingPanel.Lyrics),
        retryLyrics,
        offlineView.isOffline,
    ) { id, visible, _, offline -> id.takeIf { visible }?.let { it to offline } }
        .flatMapLatest { request -> if (request == null) flowOf(null) else loadLyrics(request.first, request.second) }
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
        offlineView.isOffline,
    ) { (artistId, known), visible, _, offline -> RelatedRequest(artistId, known, visible, offline) }
        .flatMapLatest { request ->
            when {
                !request.visible -> flowOf(null)

                request.artistId == null ->
                    flowOf(if (request.known) RelatedUiState.Unavailable else RelatedUiState.Loading)

                else -> relatedPanel(request.artistId, request.offline) { artistId ->
                    loadOrNull("related songs") { artists.similarSongs(artistId, RELATED_COUNT) }
                }
            }
        }
        .holdLastNonNull(RelatedUiState.Loading)
        .combine(combine(markedSong, offlineView.songAvailability, ::Pair)) { related, (current, availability) ->
            related.shownWhile(current, availability)
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RelatedUiState.Loading)

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

    fun cycleRepeatMode() = playback.setRepeatMode(uiState.value.repeatMode.next())

    fun toggleFavorite() = playback.toggleFavorite()

    /** Starts the sleep timer and confirms it, with Cancel; Cancel works after Now Playing collapses. */
    fun setSleepTimer(choice: SleepTimerChoice) {
        playback.setSleepTimer(choice.toSleepTimer(System.currentTimeMillis()))
        sleepTimerConfirmations.confirm(choice) { playback.setSleepTimer(null) }
    }

    /**
     * "+15 min" on the running timer; confirms the new time left, with Cancel, like starting one.
     * Does nothing when no timer runs.
     */
    fun extendSleepTimer() {
        val running = uiState.value.sleepTimer ?: return
        val now = System.currentTimeMillis()
        val extended = running.extendedBy15Min(now)
        playback.setSleepTimer(extended)
        sleepTimerConfirmations.confirm(SleepTimerChoice.Minutes(extended.minutesLeft(now))) {
            playback.setSleepTimer(null)
        }
    }

    fun cancelSleepTimer() {
        playback.setSleepTimer(null)
        sleepTimerConfirmations.takeDown()
    }

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
                SnackbarText.of(R.string.feature_nowplaying_queue_saved, name.trim())
            } catch (_: RemoteException) {
                SnackbarText.of(R.string.feature_nowplaying_queue_save_failed)
            }
            snackbars.post(message)
        }
    }

    fun clearQueue() = playback.clearQueue()

    /** Stores [speed], one of the presets; the playback service applies it to the player. */
    fun setPlaybackSpeed(speed: Float) {
        chosenSpeed.value = speed
        chosenSpeedJob?.cancel()
        chosenSpeedJob = viewModelScope.launch {
            // Until the player plays at it, or answers with another speed.
            player.state.map { it.speed }.distinctUntilChanged().withIndex()
                .first { (index, playing) -> playing == speed || index > 0 }
            chosenSpeed.value = null
        }
        viewModelScope.launch { preferences.update { it.copy(playbackSpeed = speed) } }
    }

    private fun loadLyrics(songId: String, offline: Boolean): Flow<LyricsUiState> = flow {
        emit(LyricsUiState.Loading)
        emit(lyricsUiState(loadOrNull("lyrics") { lyricsRepository.lyrics(songId) }, offline))
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

private data class RelatedRequest(val artistId: String?, val known: Boolean, val visible: Boolean, val offline: Boolean)

/** Emits [initial], then every non-null upstream value: null means "keep the last value". */
private fun <T : Any> Flow<T?>.holdLastNonNull(initial: T): Flow<T> = flow {
    emit(initial)
    collect { if (it != null) emit(it) }
}.distinctUntilChanged()
