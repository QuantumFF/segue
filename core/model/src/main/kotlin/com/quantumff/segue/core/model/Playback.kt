package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the player is doing, for the mini player, now playing, and queue screens. */
data class PlayerUiState(
    val currentItem: QueueItem? = null,
    /** Index of [currentItem] in [queue], or -1 when the queue is empty. */
    val currentQueueIndex: Int = -1,
    /**
     * The player plays as the user sees it: it will play when ready and is neither stopped nor at
     * the end of the queue. True while [isBuffering] towards playing, when the position stands still.
     */
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** The queue played to its end: the player stopped after its last item. */
    val hasEnded: Boolean = false,
    /** Updated about twice a second while the UI observes this state and playback runs; otherwise on events. */
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    /** The queue, in the order it plays. */
    val queue: ImmutableList<QueueItem> = persistentListOf(),
    val repeatMode: RepeatMode = RepeatMode.Off,
    /** A newer queue saved by another device, to offer as "Continue from <device>". */
    val remoteQueue: RemoteQueueOffer? = null,
    val sleepTimer: SleepTimer? = null,
    /** The current song failed to play; null once playback moves to another item or a retry plays. */
    val playbackError: PlaybackError? = null,
    /** What the player is actually playing for [currentItem]; null until it is known. */
    val streamFormat: StreamFormat? = null,
    /** Where the queue came from, for Now Playing's top bar. */
    val queueSource: QueueSource = QueueSource.Unknown,
)

/**
 * The stream format: the codec and bit rate of what the player decoded or requested for the
 * current song. A server transcode ("OPUS", 128) or a downloaded file, which may differ from the
 * library song's own file. Unknown values are null.
 */
data class StreamFormat(
    /** "FLAC", "MP3", "OPUS". */
    val codec: String,
    val bitRateKbps: Int? = null,
    /** Hz; for lossless streams. */
    val sampleRateHz: Int? = null,
    /** Bits per sample; null for lossy streams. */
    val bitDepth: Int? = null,
)

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

/** A song that failed to play ([songId], titled [title]), and why. */
data class PlaybackError(val songId: String, val title: String, val kind: PlaybackErrorKind) {
    /** Only a network error may go away by trying again; the others would fail the same way. */
    val canRetry: Boolean get() = kind == PlaybackErrorKind.Network
}

/** Why a song failed to play. */
enum class PlaybackErrorKind {
    /** The server could not be reached, or did not answer in time. */
    Network,

    /** The device cannot play the song's format. */
    Unsupported,

    /** The song's file is missing, on the server or on the device. */
    NotFound,

    /** Anything else. */
    Other,
}

/** When playback pauses by itself. */
sealed interface SleepTimer {
    /** Pause at the wall-clock time [endsAtMillis] (epoch milliseconds). */
    data class AtTime(val endsAtMillis: Long) : SleepTimer

    /** Pause when the current track ends. */
    data object EndOfTrack : SleepTimer

    /** Pause when the next track in play order belongs to another album, or the queue ends. */
    data object EndOfAlbum : SleepTimer
}
