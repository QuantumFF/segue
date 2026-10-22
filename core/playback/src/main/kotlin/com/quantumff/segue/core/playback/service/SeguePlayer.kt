package com.quantumff.segue.core.playback.service

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.playback.sync.QueueState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The session's player: ExoPlayer with shuffle switched off for good, and the queue's
 * [queueSource], so the queue it reports for queue sync and the snapshot carries its source.
 *
 * Segue shuffles only when songs are queued, by queueing them in shuffled order
 * (docs/adr/0002); a running queue is never reshuffled. So the queue order is always the play
 * order. Generic controllers (notification, Bluetooth, Android Auto) are not offered a shuffle
 * toggle, and a stray shuffle request is ignored.
 */
class SeguePlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {
    private val mutableQueueSource = MutableStateFlow<QueueSource>(QueueSource.Unknown)

    /** Where the queue came from. Set it before the queue it names, so the queue's events report it. */
    val queueSource: StateFlow<QueueSource> = mutableQueueSource.asStateFlow()

    fun setQueueSource(source: QueueSource) {
        mutableQueueSource.value = source
    }

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().remove(Player.COMMAND_SET_SHUFFLE_MODE).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command != Player.COMMAND_SET_SHUFFLE_MODE && super.isCommandAvailable(command)

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) = Unit

    override fun getShuffleModeEnabled(): Boolean = false

    /**
     * Restores a saved queue exactly: [items] (matching [state]'s songs), the current item and
     * position, and its source.
     */
    fun restore(items: List<MediaItem>, state: QueueState) {
        setQueueSource(state.source)
        setMediaItems(items, state.currentIndex, state.positionMillis)
    }

    /** The queue, for queue sync and the snapshot. */
    fun queueState(): QueueState = QueueState(
        songIds = List(exo.mediaItemCount) { exo.getMediaItemAt(it).mediaId },
        currentIndex = exo.currentMediaItemIndex,
        positionMillis = exo.currentPosition,
        source = queueSource.value,
    )

    /** Song IDs after the current item, at most [limit]. */
    fun upNext(limit: Int): List<String> {
        val from = exo.currentMediaItemIndex + 1
        return (from until minOf(exo.mediaItemCount, from + limit)).map { exo.getMediaItemAt(it).mediaId }
    }
}
