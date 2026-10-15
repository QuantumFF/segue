package com.quantumff.segue.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.OfflineModeRepository
import com.quantumff.segue.core.data.download.downloadedWhileOffline
import com.quantumff.segue.core.model.OfflinePlayback
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shell snackbars: why a play intent did nothing, or that another device's queue was taken over. */
sealed interface PlayerMessage {
    data object NothingToPlay : PlayerMessage

    data object InstantMixFailed : PlayerMessage

    /** "Continued from <device>", with Undo when [canUndo]. */
    data class ContinuedFrom(val deviceName: String, val canUndo: Boolean) : PlayerMessage
}

/**
 * The shell's link to playback: the state the mini player and player bar show, their commands,
 * and the [PlayActions] every browse screen raises. Activity-scoped, so intents that need the
 * network (instant mix) finish even if the user navigates away.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val offlineMode: OfflineModeRepository,
    private val downloads: DownloadRepository,
) : ViewModel() {
    private val isOffline: StateFlow<Boolean> = offlineMode.isOffline

    val player: StateFlow<ShellPlayerState> = playback.eventState
        .map { it.toShellPlayerState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ShellPlayerState())

    /**
     * Position in milliseconds. Collect it only inside a visible player surface and read it in the
     * draw phase: collecting it is what runs the position ticker while music plays.
     */
    val positionMillis: StateFlow<Long> = playback.state
        .map { it.positionMillis }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0L)

    private val messageChannel = Channel<PlayerMessage>(Channel.BUFFERED)
    val messages: Flow<PlayerMessage> = merge(
        messageChannel.receiveAsFlow(),
        playback.autoAdoptions.map { PlayerMessage.ContinuedFrom(it.deviceName, it.canUndo) },
    )

    val playActions = PlayActions(
        // Offline, songs that are not downloaded are skipped (DESIGN.md §5.2: they cannot play).
        onPlaySongs = { songs, startIndex, shuffle ->
            if (!isOffline.value) {
                playback.play(songs, startIndex, shuffle)
            } else {
                viewModelScope.launch {
                    val selection = OfflinePlayback.playable(songs, startIndex, isOffline = true, downloadedIds())
                    if (selection == null) {
                        messageChannel.send(PlayerMessage.NothingToPlay)
                    } else {
                        playback.play(selection.songs, selection.startIndex, shuffle)
                    }
                }
            }
        },
        onShuffleAlbums = { source ->
            viewModelScope.launch {
                // Offline: downloaded albums only (DESIGN.md §1, offline is first-class).
                val offline = offlineMode.downloadedWhileOffline(downloads).first()
                if (!playback.shuffleAlbums(source, offline)) messageChannel.send(PlayerMessage.NothingToPlay)
            }
        },
        onPlayNext = { songs -> withQueueable(songs, playback::playNext) },
        onAddToQueue = { songs -> withQueueable(songs, playback::addToQueue) },
        onStartInstantMix = { seed ->
            viewModelScope.launch {
                // The mix comes from the server (getSimilarSongs2), so there is none offline.
                if (isOffline.value) {
                    messageChannel.send(PlayerMessage.InstantMixFailed)
                    return@launch
                }
                val started = try {
                    playback.playInstantMix(seed)
                } catch (e: RemoteException) {
                    Log.w(TAG, "Instant mix failed", e)
                    false
                }
                if (!started) messageChannel.send(PlayerMessage.InstantMixFailed)
            }
        },
    )

    /** Songs to queue: all of them online; offline only the downloaded ones (read from Room, so never stale). */
    private fun withQueueable(songs: List<Song>, queue: (List<Song>) -> Unit) {
        if (!isOffline.value) return queue(songs)
        viewModelScope.launch {
            val kept = OfflinePlayback.queueable(songs, isOffline = true, downloadedIds())
            if (kept.isEmpty()) messageChannel.send(PlayerMessage.NothingToPlay) else queue(kept)
        }
    }

    private suspend fun downloadedIds(): Set<String> = downloads.downloadedSongIds.first()

    fun shellActions() = ShellPlayerActions(
        onPlayPause = playback::togglePlayPause,
        onNext = playback::skipToNext,
        onPrevious = playback::skipToPrevious,
        onSkipTo = playback::skipTo,
        onCycleShuffle = ::cycleShuffleMode,
        onCycleRepeat = ::cycleRepeatMode,
        onContinueRemote = playback::adoptRemoteQueue,
        onDismissRemote = playback::dismissRemoteQueue,
    )

    /** The "Continued from" snackbar's Undo: the local queue comes back. */
    fun undoAutoAdoption() = playback.undoAutoAdoption()

    /** Off → Tracks → Albums → Off (DESIGN.md §4.7). */
    fun cycleShuffleMode() = playback.setShuffleMode(player.value.shuffleMode.cycled())

    /** Off → All → One → Off. */
    fun cycleRepeatMode() = playback.setRepeatMode(player.value.repeatMode.cycled())

    private inline fun <reified E : Enum<E>> E.cycled(): E = enumValues<E>().let { it[(ordinal + 1) % it.size] }

    private companion object {
        const val TAG = "PlayerViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
