package com.quantumff.segue.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.OfflineModeRepository
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    offlineMode: OfflineModeRepository,
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
        // The service queues what can play: offline only downloaded songs (DESIGN.md §1, §5.2).
        onPlaySongs = { songs, startIndex, shuffle -> queue { playback.play(songs, startIndex, shuffle) } },
        onPlayAlbums = { request -> queue { playback.playAlbums(request) } },
        onPlayNext = { songs -> queue { playback.playNext(songs) } },
        onAddToQueue = { songs -> queue { playback.addToQueue(songs) } },
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

    /** Runs a play intent; when the service queued nothing, says so. */
    private fun queue(intent: suspend () -> Boolean) {
        viewModelScope.launch {
            if (!intent()) messageChannel.send(PlayerMessage.NothingToPlay)
        }
    }

    fun shellActions() = ShellPlayerActions(
        onPlayPause = playback::togglePlayPause,
        onNext = playback::skipToNext,
        onPrevious = playback::skipToPrevious,
        onSkipTo = playback::skipTo,
        onCycleRepeat = ::cycleRepeatMode,
        onContinueRemote = playback::adoptRemoteQueue,
        onDismissRemote = playback::dismissRemoteQueue,
    )

    /** The "Continued from" snackbar's Undo: the local queue comes back. */
    fun undoAutoAdoption() = playback.undoAutoAdoption()

    fun cycleRepeatMode() = playback.setRepeatMode(player.value.repeatMode.next())

    private companion object {
        const val TAG = "PlayerViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
