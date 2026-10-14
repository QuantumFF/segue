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
    /** The queue in play order: shuffled when shuffle is on. */
    val queue: ImmutableList<QueueItem> = persistentListOf(),
    val shuffleMode: ShuffleMode = ShuffleMode.Off,
    val repeatMode: RepeatMode = RepeatMode.Off,
    /** A newer queue saved by another device, to offer as "Continue from <device>". */
    val remoteQueue: RemoteQueueOffer? = null,
    val sleepTimer: SleepTimer? = null,
)

/**
 * One entry of the play queue. The same song can appear more than once, so key lists by
 * [timelineIndex], which is unique within one queue state.
 */
data class QueueItem(
    val songId: String,
    /** Position in the unshuffled queue. Commands that edit the queue take this index. */
    val timelineIndex: Int,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val coverArtId: String?,
    val durationMillis: Long,
    val isStarred: Boolean,
)

enum class RepeatMode {
    Off,
    All,
    One,
}

/** Another device's saved queue, newer than this device's. */
data class RemoteQueueOffer(val deviceName: String, val trackCount: Int, val currentTitle: String?)

/** When playback pauses by itself. */
sealed interface SleepTimer {
    /** Pause at the wall-clock time [endsAtMillis] (epoch milliseconds). */
    data class AtTime(val endsAtMillis: Long) : SleepTimer

    /** Pause when the current track ends. */
    data object EndOfTrack : SleepTimer

    /** Pause when the next track in play order belongs to another album, or the queue ends. */
    data object EndOfAlbum : SleepTimer
}

/** Where "Shuffle albums" draws its albums from. */
sealed interface AlbumShuffleSource {
    data object Library : AlbumShuffleSource

    data class Artist(val artistId: String) : AlbumShuffleSource

    data class Genre(val name: String) : AlbumShuffleSource

    /** The playlist's tracks grouped by album; only tracks on the playlist are queued. */
    data class Playlist(val playlistId: String) : AlbumShuffleSource
}
