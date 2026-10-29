package com.quantumff.segue.feature.queue

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import org.junit.Test

/** The queue's "Removed <title>" snackbars with Undo (DESIGN.md §4.8). */
class QueueRemovalsTest {
    private val snackbars = Snackbars()
    private val removals = QueueRemovals(snackbars)
    private val undone = mutableListOf<String>()

    private fun remove(title: String) = removals.offerUndo(title) { undone += title }

    @Test
    fun `a removal says what was removed and offers Undo`() {
        remove("Nude")

        val shown = snackbars.current.value!!
        assertThat(shown.text).isEqualTo(SnackbarText.of(R.string.feature_queue_removed, "Nude"))
        snackbars.onAction(shown)
        assertThat(undone).containsExactly("Nude")
    }

    @Test
    fun `a newer removal takes the earlier one's Undo down`() {
        remove("Nude")
        remove("Reckoner")

        // The player puts back only the last removal, so only its snackbar stays.
        assertThat(snackbars.current.value?.text).isEqualTo(SnackbarText.of(R.string.feature_queue_removed, "Reckoner"))
        snackbars.onAction(snackbars.current.value!!)
        assertThat(undone).containsExactly("Reckoner")
        assertThat(snackbars.current.value).isNull()
    }
}
