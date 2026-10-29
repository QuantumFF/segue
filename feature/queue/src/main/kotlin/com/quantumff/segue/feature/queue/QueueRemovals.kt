package com.quantumff.segue.feature.queue

import com.quantumff.segue.core.data.snackbar.SnackbarAction
import com.quantumff.segue.core.data.snackbar.SnackbarMessage
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars

/**
 * "Removed <title>" with Undo (DESIGN.md §4.8), posted to the app's [Snackbars] so it outlives the
 * Queue panel: it stays when Now Playing switches panels or collapses. The player puts back only
 * the last removal, so a newer removal takes down the snackbar of the one before.
 */
class QueueRemovals(private val snackbars: Snackbars) {
    private var last: SnackbarMessage? = null

    /** [title] was removed from the queue; Undo runs [onUndo]. */
    fun removed(title: String, onUndo: () -> Unit) {
        last?.let(snackbars::onDismiss)
        last =
            snackbars.post(SnackbarText.of(R.string.feature_queue_removed, title), SnackbarAction.undo(onUndo = onUndo))
    }
}
