package com.quantumff.segue.core.data.play

import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.InstantMixSeed
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import kotlinx.coroutines.flow.Flow

/**
 * Every play intent a screen raises goes through here: play, shuffle, play next or add to queue
 * of songs, an album, a playlist or an artist; "Play albums" of an album source; instant mix.
 *
 * Intents run in the application scope, so one finishes after the user leaves the screen that
 * raised it; a caller that stops waiting does not cancel it. Each call returns once the intent has
 * queued something or given up. Offline only downloaded songs are queued, and there is no instant
 * mix. When an intent queues nothing, [messages] says why; screens do not report it themselves.
 */
interface PlayIntents {
    /** Why an intent queued nothing, for the shell to show. */
    val messages: Flow<PlayIntentMessage>

    /**
     * Queues [target] at [placement]. [shuffle] queues the songs in a fresh shuffled order (tracks
     * shuffle); it and [PlayTarget.Songs.startIndex] apply when replacing the queue.
     */
    suspend fun play(target: PlayTarget, placement: QueuePlacement = QueuePlacement.Replace, shuffle: Boolean = false)

    /** "Play albums" or "Shuffle albums" (albums shuffle) of an album source. */
    suspend fun playAlbums(request: AlbumPlayRequest)

    /** Replaces the queue with an instant mix from [seed]. */
    suspend fun instantMix(seed: InstantMixSeed)
}

enum class PlayIntentMessage {
    /** Nothing could play: no songs, or offline none of them is downloaded. */
    NothingToPlay,

    /** The songs had to come from the server, which failed. */
    LoadFailed,

    /** The server sent no mix, or failed. */
    InstantMixFailed,

    /** Offline: an instant mix needs the server. */
    InstantMixOffline,
}
