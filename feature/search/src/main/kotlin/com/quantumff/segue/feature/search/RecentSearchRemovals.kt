package com.quantumff.segue.feature.search

import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.data.snackbar.SnackbarAction
import com.quantumff.segue.core.data.snackbar.SnackbarSlot
import com.quantumff.segue.core.data.snackbar.SnackbarText
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.data.snackbar.post
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Removing recent searches, by their ✕ or swipe, with "Removed" and Undo in the app's [Snackbars].
 * Undo puts the search back where it was, even after Search has closed, so the writes run in the
 * app's scope. As with the queue, a newer removal takes down the Undo of the one before, so two
 * Undos cannot restore out of order. Call from the main thread.
 */
@Singleton
class RecentSearchRemovals @Inject constructor(
    private val preferences: UserPreferencesRepository,
    snackbars: Snackbars,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val undo = SnackbarSlot<Unit>(snackbars)

    /** Removes [query], which is at [index] in the recent searches, and offers Undo. */
    fun remove(query: String, index: Int) {
        scope.launch { preferences.removeRecentSearch(query) }
        undo.post(
            SnackbarText.of(R.string.feature_search_removed),
            SnackbarAction.undo { scope.launch { preferences.restoreRecentSearch(query, index) } },
        )
    }

    /** Removes every recent search, after its confirm; the last removal's Undo goes with them. */
    fun clear() {
        undo.takeDown()
        scope.launch { preferences.clearRecentSearches() }
    }
}
