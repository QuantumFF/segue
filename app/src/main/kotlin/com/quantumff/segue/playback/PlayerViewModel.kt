package com.quantumff.segue.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why a play intent did nothing, shown as a snackbar. */
enum class PlayerMessage {
    NothingToPlay,
    InstantMixFailed,
}

/**
 * The shell's link to playback: the state the mini player and player bar show, their commands,
 * and the [PlayActions] every browse screen raises. Activity-scoped, so intents that need the
 * network (instant mix) finish even if the user navigates away.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(private val playback: PlaybackController) : ViewModel() {
    val player: StateFlow<ShellPlayerState> = playback.state
        .map { it.toShellPlayerState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ShellPlayerState())

    /** Position in milliseconds. Read it in the draw phase only; it ticks while music plays. */
    val positionMillis: StateFlow<Long> = playback.state
        .map { it.positionMillis }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0L)

    private val messageChannel = Channel<PlayerMessage>(Channel.BUFFERED)
    val messages: Flow<PlayerMessage> = messageChannel.receiveAsFlow()

    val playActions = PlayActions(
        onPlaySongs = { songs, startIndex, shuffle -> playback.play(songs, startIndex, shuffle) },
        onShuffleAlbums = { source ->
            viewModelScope.launch {
                if (!playback.shuffleAlbums(source)) messageChannel.send(PlayerMessage.NothingToPlay)
            }
        },
        onPlayNext = playback::playNext,
        onAddToQueue = playback::addToQueue,
        onStartInstantMix = { seed ->
            viewModelScope.launch {
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

    fun shellActions() = ShellPlayerActions(
        onPlayPause = playback::togglePlayPause,
        onNext = playback::skipToNext,
        onPrevious = playback::skipToPrevious,
        onSkipTo = playback::skipTo,
        onCycleShuffle = ::cycleShuffleMode,
        onCycleRepeat = ::cycleRepeatMode,
    )

    fun cycleShuffleMode() {
        val mode = player.value.shuffleMode
        playback.setShuffleMode(ShuffleMode.entries[(mode.ordinal + 1) % ShuffleMode.entries.size])
    }

    fun cycleRepeatMode() {
        val mode = player.value.repeatMode
        playback.setRepeatMode(RepeatMode.entries[(mode.ordinal + 1) % RepeatMode.entries.size])
    }

    private companion object {
        const val TAG = "PlayerViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
