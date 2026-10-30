package com.quantumff.segue.core.data.play

import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.InstantMixSeed
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import kotlinx.coroutines.flow.Flow

/**
 * Every play intent a screen raises goes through here: play, shuffle, play next or add to queue
 * of songs, an album, a playlist or an artist; "Play albums" of an album source; instant mix.
 *
 * Intents run in the application scope, so one finishes after the user leaves the screen that
 * raised it; a caller that stops waiting does not cancel it. Each call returns once the intent has
 * queued something or given up. Offline only downloaded songs are queued, and there is no instant
 * mix. When an intent queues nothing, [messages] says why; when Play next or Add to queue queues
 * something, [messages] confirms it. Screens do not report either themselves.
 *
 * An intent that replaces the queue records its queue source, which Now Playing names: the caller
 * passes it, since only the caller knows the name and where the songs came from (an album page plays
 * its songs). Play next and Add to queue leave the queue source alone.
 */
interface PlayIntents {
    /** What an intent queued, or why it queued nothing, for the shell to show. */
    val messages: Flow<PlayIntentMessage>

    /**
     * Queues [target] at [placement]. [shuffle] queues the songs in a fresh shuffled order (tracks
     * shuffle); it, [PlayTarget.Songs.startIndex] and [source] apply when replacing the queue; at
     * another placement [source] names what was queued. Play next and Add to queue confirm what they
     * queued unless [confirm] is false: a swipe is its own feedback (DESIGN.md §5.2). Replacing the
     * queue never confirms: the music starting is confirmation enough.
     */
    suspend fun play(
        target: PlayTarget,
        placement: QueuePlacement = QueuePlacement.Replace,
        shuffle: Boolean = false,
        source: QueueSource = QueueSource.Unknown,
        confirm: Boolean = true,
    )

    /**
     * "Play albums" or "Shuffle albums" (albums shuffle) of an album source; [source] applies when
     * replacing the queue. Placed next or at the end, it confirms what it queued.
     */
    suspend fun playAlbums(request: AlbumPlayRequest, source: QueueSource)

    /** Replaces the queue with an instant mix from [seed], its queue source named after the seed. */
    suspend fun instantMix(seed: InstantMixSeed)
}

/** What a play intent did, or why it did nothing. */
sealed interface PlayIntentMessage {
    /**
     * Play next or Add to queue ([placement]) queued [count] songs, offline only the downloaded ones.
     * [name] is what was queued: the song's title when it was one song, else the album, playlist,
     * artist or genre's name; null when it has none, as for a selection.
     */
    data class Queued(val count: Int, val placement: QueuePlacement, val name: String?) : PlayIntentMessage

    /**
     * An Instant mix started. [replacedQueue]: it replaced a queue, which Undo can bring back until
     * the next queue change; over an empty queue there is nothing to undo.
     */
    data class MixStarted(val replacedQueue: Boolean) : PlayIntentMessage

    /** Nothing could play: no songs, or offline none of them is downloaded. */
    data object NothingToPlay : PlayIntentMessage

    /** The songs had to come from the server, which failed. */
    data object LoadFailed : PlayIntentMessage

    /** The server sent no mix, or failed. */
    data object InstantMixFailed : PlayIntentMessage

    /** Offline: an instant mix needs the server. */
    data object InstantMixOffline : PlayIntentMessage
}
