package com.quantumff.segue.feature.queue

import com.quantumff.segue.core.data.snackbar.SnackbarAction
import com.quantumff.segue.core.data.snackbar.SnackbarSlot
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.data.snackbar.post

/**
 * "Removed <title>" with Undo (DESIGN.md §4.8), posted to the app's [Snackbars] so it outlives the
 * Queue panel: it stays when Now Playing switches panels or collapses. The player puts back only
 * the last removal, so a newer removal takes down the snackbar of the one before.
 */
class QueueRemovals(snackbars: Snackbars) {
    private val slot = SnackbarSlot<Unit>(snackbars)

    /** Offers Undo of removing [title] from the queue, which runs [onUndo]. */
    fun offerUndo(title: String, onUndo: () -> Unit) {
        slot.post(SnackbarText.of(R.string.feature_queue_removed, title), SnackbarAction.undo(onUndo = onUndo))
    }
}
