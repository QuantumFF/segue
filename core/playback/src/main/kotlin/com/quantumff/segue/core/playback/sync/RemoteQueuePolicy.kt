package com.quantumff.segue.core.playback.sync

import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.RemotePlayQueue

/** What to do with the queue saved on the server. */
sealed interface RemoteQueueDecision {
    /** Show "Continue from <device>". */
    data object Offer : RemoteQueueDecision

    /** Take it over without asking (the user opted in). */
    data object Adopt : RemoteQueueDecision

    data class Ignore(val reason: IgnoreReason) : RemoteQueueDecision

    enum class IgnoreReason {
        /** Nothing saved. */
        Empty,

        /** This device saved it: our own upload coming back. */
        OwnSave,

        /** Same songs and current track as the local queue. */
        SameQueue,

        /** Not newer than the local snapshot, or its age is unknown while a local queue exists. */
        NotNewer,

        /** The user already dismissed or adopted this save. */
        AlreadyHandled,
    }
}

/** Decides whether a remote play queue should replace the local one. See docs/PLAN.md, "Server queue sync". */
object RemoteQueuePolicy {
    /**
     * [ownClientName] is this device's `changedBy`. [handledChangedAt] is the `changed` time of the
     * last remote queue the user dismissed or adopted (0 for one without a timestamp). Auto adopt
     * never replaces a queue that is playing here; that is offered instead.
     */
    @Suppress("LongParameterList", "ReturnCount")
    fun decide(
        remote: RemotePlayQueue?,
        local: QueueSnapshot?,
        ownClientName: String,
        autoAdopt: Boolean,
        isPlayingLocally: Boolean,
        handledChangedAt: Long?,
    ): RemoteQueueDecision {
        if (remote == null || remote.songs.isEmpty()) return ignore(RemoteQueueDecision.IgnoreReason.Empty)
        if (remote.changedBy == ownClientName) return ignore(RemoteQueueDecision.IgnoreReason.OwnSave)
        if (local != null) {
            val sameQueue = local.currentIndex == remote.currentIndex && local.songIds == remote.songs.map { it.id }
            if (sameQueue) return ignore(RemoteQueueDecision.IgnoreReason.SameQueue)
            val changedAt = remote.changedAt
            if (changedAt == null || changedAt <= local.updatedAt) {
                return ignore(RemoteQueueDecision.IgnoreReason.NotNewer)
            }
        }
        if (handledChangedAt != null && handledKey(remote) == handledChangedAt) {
            return ignore(RemoteQueueDecision.IgnoreReason.AlreadyHandled)
        }
        return if (autoAdopt && !isPlayingLocally) RemoteQueueDecision.Adopt else RemoteQueueDecision.Offer
    }

    /** The value to remember as handled for [remote]. */
    fun handledKey(remote: RemotePlayQueue): Long = remote.changedAt ?: 0L

    private fun ignore(reason: RemoteQueueDecision.IgnoreReason) = RemoteQueueDecision.Ignore(reason)
}
