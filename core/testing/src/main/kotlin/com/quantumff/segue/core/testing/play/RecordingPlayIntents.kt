package com.quantumff.segue.core.testing.play

import com.quantumff.segue.core.data.play.PlayIntentMessage
import com.quantumff.segue.core.data.play.PlayIntents
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.InstantMixSeed
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.QueuePlacement
import kotlinx.coroutines.flow.MutableSharedFlow

/** Records the play intents a screen raises, in order, without playing anything. */
class RecordingPlayIntents : PlayIntents {
    data class Play(val target: PlayTarget, val placement: QueuePlacement, val shuffle: Boolean)

    /** Every intent, in order: [Play], [AlbumPlayRequest] or [InstantMixSeed]. */
    val intents = mutableListOf<Any>()

    val plays: List<Play> get() = intents.filterIsInstance<Play>()
    val albumRequests: List<AlbumPlayRequest> get() = intents.filterIsInstance<AlbumPlayRequest>()
    val mixes: List<InstantMixSeed> get() = intents.filterIsInstance<InstantMixSeed>()

    /** Emit here to stand in for the real module's feedback. */
    override val messages = MutableSharedFlow<PlayIntentMessage>(extraBufferCapacity = 8)

    override suspend fun play(target: PlayTarget, placement: QueuePlacement, shuffle: Boolean) {
        intents += Play(target, placement, shuffle)
    }

    override suspend fun playAlbums(request: AlbumPlayRequest) {
        intents += request
    }

    override suspend fun instantMix(seed: InstantMixSeed) {
        intents += seed
    }
}
