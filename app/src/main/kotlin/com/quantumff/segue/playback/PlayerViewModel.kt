package com.quantumff.segue.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.R
import com.quantumff.segue.core.data.play.PlayIntentMessage
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.data.snackbar.SnackbarAction
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * The shell's link to playback: the state the mini player and player bar show, their commands,
 * and, posted to [Snackbars], what a play intent raised on any screen ([PlayIntents.messages]) queued
 * or why it queued nothing, and that another device's queue was adopted.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackController,
    playIntents: PlayIntents,
    snackbars: Snackbars,
) : ViewModel() {
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

    init {
        playIntents.messages
            .onEach { snackbars.post(it.snackbarText()) }
            .launchIn(viewModelScope)
        // "Continue from <device>" that adopted nothing, as when offline none of its songs is downloaded.
        playback.continueFailures
            .onEach { snackbars.post(PlayIntentMessage.NothingToPlay.snackbarText()) }
            .launchIn(viewModelScope)
        // DESIGN.md §4.8: with auto-continue on, a snackbar replaces the banner, with Undo.
        playback.autoAdoptions
            .onEach { adoption ->
                snackbars.post(
                    SnackbarText.of(R.string.player_continued_from, adoption.deviceName),
                    if (adoption.canUndo) SnackbarAction.undo(onUndo = playback::undoAutoAdoption) else null,
                )
            }
            .launchIn(viewModelScope)
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

    /** The playback error snackbar's Skip: plays the next item. */
    fun skipPastError() = playback.skipPastError()

    /** The playback error snackbar's Retry, for a network error: prepares the song again and plays it. */
    fun retryPlayback() = playback.retry()

    fun cycleRepeatMode() = playback.setRepeatMode(player.value.repeatMode.next())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** "Playing next: <title>" for one song named, else "Added 12 songs to the queue"; failures say why. */
internal fun PlayIntentMessage.snackbarText(): SnackbarText = when (this) {
    is PlayIntentMessage.Queued -> queuedText()
    PlayIntentMessage.NothingToPlay -> SnackbarText.of(R.string.player_nothing_to_play)
    PlayIntentMessage.LoadFailed -> SnackbarText.of(R.string.player_load_failed)
    PlayIntentMessage.InstantMixFailed -> SnackbarText.of(R.string.player_instant_mix_failed)
    PlayIntentMessage.InstantMixOffline -> SnackbarText.of(R.string.player_instant_mix_offline)
}

private fun PlayIntentMessage.Queued.queuedText(): SnackbarText {
    val next = placement == QueuePlacement.Next
    val song = name?.takeIf { count == 1 }
    return if (song != null) {
        SnackbarText.of(if (next) R.string.player_playing_next else R.string.player_added_to_queue, song)
    } else {
        SnackbarText.plural(
            if (next) R.plurals.player_playing_songs_next else R.plurals.player_added_songs,
            count,
            count,
        )
    }
}
