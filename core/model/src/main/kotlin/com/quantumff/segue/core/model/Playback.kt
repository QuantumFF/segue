package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the player is doing, for the mini player, now playing, and queue screens. */
data class PlayerUiState(
    val currentItem: QueueItem? = null,
    /** Index of [currentItem] in [queue], or -1 when the queue is empty. */
    val currentQueueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** Updated about twice a second while the UI observes this state and playback runs; otherwise on events. */
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    /** The queue, in the order it plays. */
    val queue: ImmutableList<QueueItem> = persistentListOf(),
    val repeatMode: RepeatMode = RepeatMode.Off,
    /** A newer queue saved by another device, to offer as "Continue from <device>". */
    val remoteQueue: RemoteQueueOffer? = null,
    val sleepTimer: SleepTimer? = null,
)

/**
 * The current song and whether it plays, for marking it in song lists (DESIGN.md §5.2). Lists match
 * rows by [songId], so a song listed twice is marked twice.
 */
data class PlayingSong(val songId: String, val isPlaying: Boolean)

/** The song to mark in song lists, or null when nothing is current. */
fun PlayerUiState.playingSong(): PlayingSong? = currentItem?.let { PlayingSong(it.songId, isPlaying) }

/**
 * One entry of the play queue. The same song can appear more than once, so key lists by
 * [timelineIndex], which is unique within one queue state.
 */
data class QueueItem(
    val songId: String,
    /** Position in the queue. Commands that edit the queue take this index. */
    val timelineIndex: Int,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val coverArtId: String?,
    val durationMillis: Long,
    val isStarred: Boolean,
)

/** Another device's saved queue, newer than this device's. */
data class RemoteQueueOffer(
    val deviceName: String,
    val trackCount: Int,
    val currentTitle: String?,
    /** Where the other device was in [currentTitle]. */
    val positionMillis: Long = 0,
    /** When the server saved it (server clock, epoch milliseconds); null if the server did not say. */
    val changedAt: Long? = null,
)

/**
 * Another device's queue was adopted without asking (the auto-continue setting): the
 * "Continued from <device>" snackbar, with Undo when [canUndo] (a local queue was replaced).
 */
data class AutoAdoption(val deviceName: String, val canUndo: Boolean)

/** When playback pauses by itself. */
sealed interface SleepTimer {
    /** Pause at the wall-clock time [endsAtMillis] (epoch milliseconds). */
    data class AtTime(val endsAtMillis: Long) : SleepTimer

    /** Pause when the current track ends. */
    data object EndOfTrack : SleepTimer

    /** Pause when the next track in play order belongs to another album, or the queue ends. */
    data object EndOfAlbum : SleepTimer
}
