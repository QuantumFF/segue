package com.quantumff.segue.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.play.PlayIntentMessage
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

/** Shell snackbars: why a play intent did nothing, or that another device's queue was taken over. */
sealed interface PlayerMessage {
    /** A play intent raised on any screen, or "Continue from <device>", queued nothing. */
    data class NotPlayed(val reason: PlayIntentMessage) : PlayerMessage

    /** "Continued from <device>", with Undo when [canUndo]. */
    data class ContinuedFrom(val deviceName: String, val canUndo: Boolean) : PlayerMessage
}

/**
 * The shell's link to playback: the state the mini player and player bar show, their commands,
 * and the snackbars for play intents raised on any screen ([PlayIntents.messages]) and for adoption.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(private val playback: PlaybackController, playIntents: PlayIntents) :
    ViewModel() {
    val player: StateFlow<ShellPlayerState> = playback.state
        .map { it.toShellPlayerState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ShellPlayerState())

    /**
     * Position in milliseconds. Collect it only inside a visible player surface and read it in the
     * draw phase: collecting it is what runs the position ticker while music plays.
     */
    val positionMillis: StateFlow<Long> = playback.tickingState
        .map { it.positionMillis }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0L)

    val messages: Flow<PlayerMessage> = merge(
        playIntents.messages.map { PlayerMessage.NotPlayed(it) },
        // "Continue from <device>" that adopted nothing, as when offline none of its songs is downloaded.
        playback.continueFailures.map { PlayerMessage.NotPlayed(PlayIntentMessage.NothingToPlay) },
        playback.autoAdoptions.map { PlayerMessage.ContinuedFrom(it.deviceName, it.canUndo) },
    )

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
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
