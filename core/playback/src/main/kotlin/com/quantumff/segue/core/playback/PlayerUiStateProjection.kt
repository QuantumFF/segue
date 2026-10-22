package com.quantumff.segue.core.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueueItem
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.playback.media.toQueueItem
import com.quantumff.segue.core.playback.service.SegueSessionCommands
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The UI's view of a player ([PlaybackController]'s MediaController): the queue, the current item,
 * transport state, the stream format (decoded tracks plus the request in the session extras), and
 * the sleep timer, playback error and queue source from the session extras. The queue is rebuilt
 * only when the player's timeline changes, not on every event or position tick. While the player
 * knows no duration yet, the current item's own duration stands in.
 *
 * Call on the player's thread. One projection follows one player at a time.
 */
internal class PlayerUiStateProjection {
    private var timeline: Timeline? = null
    private var queue: ImmutableList<QueueItem> = persistentListOf()

    fun project(player: Player, sessionExtras: Bundle): PlayerUiState {
        val queue = queue(player)
        val queueIndex = player.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val current = queue.getOrNull(queueIndex)
        val durationMillis = player.duration.takeIf { it != C.TIME_UNSET } ?: current?.durationMillis ?: 0
        return PlayerUiState(
            currentItem = current,
            currentQueueIndex = queueIndex,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMillis = player.currentPosition,
            durationMillis = durationMillis,
            queue = queue,
            repeatMode = repeatModeOf(player.repeatMode),
            sleepTimer = SegueSessionCommands.sleepTimer(sessionExtras),
            streamFormat = current?.let {
                streamFormat(
                    decoded = player.decodedAudioFormat(),
                    request = SegueSessionCommands.streamRequest(sessionExtras, it.songId),
                    durationMillis = durationMillis,
                )
            },
            playbackError = SegueSessionCommands.playbackError(sessionExtras),
            queueSource = SegueSessionCommands.queueSource(sessionExtras),
        )
    }

    private fun queue(player: Player): ImmutableList<QueueItem> {
        val current = player.currentTimeline
        if (current != timeline) {
            timeline = current
            queue = List(player.mediaItemCount) { player.getMediaItemAt(it).toQueueItem(it) }.toImmutableList()
        }
        return queue
    }
}

/** Media3's repeat mode for this one. */
internal val RepeatMode.playerRepeatMode: Int
    get() = when (this) {
        RepeatMode.Off -> Player.REPEAT_MODE_OFF
        RepeatMode.All -> Player.REPEAT_MODE_ALL
        RepeatMode.One -> Player.REPEAT_MODE_ONE
    }

/** The repeat mode for Media3's [playerRepeatMode]; an unknown one is off. */
internal fun repeatModeOf(playerRepeatMode: Int): RepeatMode =
    RepeatMode.entries.firstOrNull { it.playerRepeatMode == playerRepeatMode } ?: RepeatMode.Off
