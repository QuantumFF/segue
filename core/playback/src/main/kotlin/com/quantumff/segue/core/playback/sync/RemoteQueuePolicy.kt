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

        /** Same songs (in play order) and current track as the local queue. */
        SameQueue,

        /**
         * Not newer than the last server change this device accounted for: its own save, or a
         * queue the user already adopted or dismissed.
         */
        NotNewer,
    }
}

/**
 * Decides whether a remote play queue should replace the local one. See docs/PLAN.md, "Server queue sync".
 *
 * "Newer" never involves this device's clock. Only server timestamps are compared: the remote
 * queue's `changed` against `seen`, the server's `changed` of the newest queue this device has
 * accounted for (its own save as read back from the server, or another client's save that the
 * user adopted or dismissed). The server holds one queue, so a save by another client that
 * this device has not accounted for is always the newest state there.
 */
object RemoteQueuePolicy {
    /**
     * [ownClientName] is this device's `changedBy`. [seen] is the remembered server change time,
     * or null when this device has never accounted for one. Auto adopt never replaces a queue
     * that is playing here; that is offered instead.
     */
    @Suppress("LongParameterList", "ReturnCount")
    fun decide(
        remote: RemotePlayQueue?,
        local: QueueSnapshot?,
        ownClientName: String,
        autoAdopt: Boolean,
        isPlayingLocally: Boolean,
        seen: Long?,
    ): RemoteQueueDecision {
        if (remote == null || remote.songs.isEmpty()) return ignore(RemoteQueueDecision.IgnoreReason.Empty)
        if (remote.changedBy == ownClientName) return ignore(RemoteQueueDecision.IgnoreReason.OwnSave)
        if (seen != null && seenKey(remote) <= seen) return ignore(RemoteQueueDecision.IgnoreReason.NotNewer)
        if (local != null && local.isSameAs(remote)) return ignore(RemoteQueueDecision.IgnoreReason.SameQueue)
        return if (autoAdopt && !isPlayingLocally) RemoteQueueDecision.Adopt else RemoteQueueDecision.Offer
    }

    /**
     * The value to remember once [remote] is accounted for. A server that sends no `changed`
     * counts as 0: such a queue is offered once, and later timestamped saves are newer.
     */
    fun seenKey(remote: RemotePlayQueue): Long = remote.changedAt ?: 0L

    private fun QueueSnapshot.isSameAs(remote: RemotePlayQueue): Boolean =
        currentIndexInPlayOrder == remote.currentIndex && songIdsInPlayOrder == remote.songs.map { it.id }

    private fun ignore(reason: RemoteQueueDecision.IgnoreReason) = RemoteQueueDecision.Ignore(reason)
}
