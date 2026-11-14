package com.quantumff.segue.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.quantumff.segue.core.model.LibraryTab

/**
 * Each Library tab's place in its list and its grid, kept above the tab's page so the place
 * survives the page leaving composition and coming back.
 */
@Stable
internal class LibraryScrollStates private constructor(
    private val lists: Map<LibraryTab, LazyListState>,
    private val grids: Map<LibraryTab, LazyGridState>,
) {
    constructor() : this(
        LibraryTab.entries.associateWith { LazyListState() },
        LibraryTab.entries.associateWith { LazyGridState() },
    )

    /** The [tab]'s place when it shows a list. */
    fun list(tab: LibraryTab): LazyListState = lists.getValue(tab)

    /** The [tab]'s place when it shows a grid. */
    fun grid(tab: LibraryTab): LazyGridState = grids.getValue(tab)

    companion object {
        /** Saves each tab's first visible item and its offset, list then grid, in tab order. */
        val Saver: Saver<LibraryScrollStates, List<Int>> = Saver(
            save = { states ->
                LibraryTab.entries.flatMap { tab ->
                    val list = states.list(tab)
                    val grid = states.grid(tab)
                    listOf(
                        list.firstVisibleItemIndex,
                        list.firstVisibleItemScrollOffset,
                        grid.firstVisibleItemIndex,
                        grid.firstVisibleItemScrollOffset,
                    )
                }
            },
            restore = { saved ->
                // A tab saved by an older build with fewer tabs starts at the top.
                val places = saved.chunked(SAVED_PER_TAB).filter { it.size == SAVED_PER_TAB }
                fun place(tab: LibraryTab) = places.getOrNull(tab.ordinal) ?: List(SAVED_PER_TAB) { 0 }
                LibraryScrollStates(
                    lists = LibraryTab.entries.associateWith { tab ->
                        val (index, offset) = place(tab)
                        LazyListState(index, offset)
                    },
                    grids = LibraryTab.entries.associateWith { tab ->
                        val (_, _, index, offset) = place(tab)
                        LazyGridState(index, offset)
                    },
                )
            },
        )

        private const val SAVED_PER_TAB = 4
    }
}

/** Each Library tab's scroll state, remembered across recreation as `rememberLazyListState` is. */
@Composable
internal fun rememberLibraryScrollStates(): LibraryScrollStates =
    rememberSaveable(saver = LibraryScrollStates.Saver) { LibraryScrollStates() }
