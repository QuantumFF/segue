package com.quantumff.segue.core.testing.play

import com.quantumff.segue.core.data.play.PlayIntentMessage
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.InstantMixSeed
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import kotlinx.coroutines.flow.MutableSharedFlow

/** Records the play intents a screen raises, in order, without playing anything. */
class RecordingPlayIntents : PlayIntents {
    data class Play(
        val target: PlayTarget,
        val placement: QueuePlacement,
        val shuffle: Boolean,
        val source: QueueSource = QueueSource.Unknown,
        /** False for an intent that confirms nothing it queues, such as a swipe's. */
        val confirm: Boolean = true,
    )

    data class AlbumPlay(val request: AlbumPlayRequest, val source: QueueSource)

    /** Every intent, in order: [Play], [AlbumPlay] or [InstantMixSeed]. */
    val intents = mutableListOf<Any>()

    val plays: List<Play> get() = intents.filterIsInstance<Play>()
    val albumPlays: List<AlbumPlay> get() = intents.filterIsInstance<AlbumPlay>()
    val albumRequests: List<AlbumPlayRequest> get() = albumPlays.map { it.request }
    val mixes: List<InstantMixSeed> get() = intents.filterIsInstance<InstantMixSeed>()

    /** Emit here to stand in for the real module's feedback. */
    override val messages = MutableSharedFlow<PlayIntentMessage>(extraBufferCapacity = 8)

    override suspend fun play(
        target: PlayTarget,
        placement: QueuePlacement,
        shuffle: Boolean,
        source: QueueSource,
        confirm: Boolean,
    ) {
        intents += Play(target, placement, shuffle, source, confirm)
    }

    override suspend fun playAlbums(request: AlbumPlayRequest, source: QueueSource) {
        intents += AlbumPlay(request, source)
    }

    override suspend fun instantMix(seed: InstantMixSeed) {
        intents += seed
    }
}
