package com.quantumff.segue.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.scrollableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import com.quantumff.segue.core.designsystem.theme.LocalReduceMotion
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.model.LibraryTab
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Test tag of the pager that holds the Library's tabs. */
internal const val LIBRARY_PAGER_TAG = "library-pager"

/**
 * The pager over the Library's tabs (DESIGN.md §4.2). [pager] follows the finger; the selected tab
 * follows only the page it settles on, so a swipe that snaps back changes nothing.
 */
@Stable
internal class LibraryPager(
    val pager: PagerState,
    private val scope: CoroutineScope,
    private val reduceMotion: Boolean,
) {
    /** The reduce-motion tab switch under way, if any. */
    private var crossfade by mutableStateOf<Crossfade?>(null)
    private val progress = Animatable(1f)

    /**
     * Moves to [tab] after a tap: a glide, or with reduce motion an instant switch that crossfades
     * from the page it leaves, both drawn in place.
     */
    fun show(tab: LibraryTab) {
        scope.launch {
            val from = pager.currentPage
            if (!reduceMotion) {
                pager.animateScrollToPage(tab.ordinal)
                return@launch
            }
            if (from == tab.ordinal) {
                pager.scrollToPage(tab.ordinal)
                return@launch
            }
            val switch = Crossfade(from, tab.ordinal)
            crossfade = switch
            try {
                progress.snapTo(0f)
                pager.scrollToPage(tab.ordinal)
                progress.animateTo(1f, SegueMotion.medium())
            } finally {
                if (crossfade === switch) crossfade = null
            }
        }
    }

    /** How many pages either side of the current one to keep composed: during a crossfade, as far as the page it leaves. */
    val beyondViewportPageCount: Int get() = crossfade?.let { abs(it.to - it.from) } ?: 0

    /** Whether [page] has anything to show: all do, but during a crossfade only the two it's between. */
    fun shows(page: Int): Boolean = crossfade.let { it == null || page == it.from || page == it.to }

    /**
     * Draws [page] in place during a crossfade, over the page it replaces, at its share of the
     * crossfade. Out of a crossfade, pages lie where the pager puts them.
     */
    fun Modifier.crossfading(page: Int): Modifier = layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val switch = crossfade
            val shift = if (switch == null) {
                0
            } else {
                val position = pager.currentPage + pager.currentPageOffsetFraction
                ((position - page) * (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing)).roundToInt()
            }
            placeable.placeRelativeWithLayer(shift, 0) {
                alpha = when {
                    switch == null -> 1f
                    page == switch.from -> 1f - progress.value
                    else -> progress.value
                }
            }
        }
    }

    private class Crossfade(val from: Int, val to: Int)
}

/**
 * Remembers the Library's pager, starting at [tab]. Call it only once the last tab is known, as
 * the pager reads its first page once.
 */
@Composable
internal fun rememberLibraryPager(tab: LibraryTab): LibraryPager {
    val pager = rememberPagerState(initialPage = tab.ordinal) { LibraryTab.entries.size }
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    return remember(pager, scope, reduceMotion) { LibraryPager(pager, scope, reduceMotion) }
}

/** The Library's tabs side by side in [libraryPager]; [page] draws one, with the modifier it's given. */
@Composable
internal fun LibraryPages(
    libraryPager: LibraryPager,
    modifier: Modifier = Modifier,
    page: @Composable (tab: LibraryTab, modifier: Modifier) -> Unit,
) {
    HorizontalPager(
        state = libraryPager.pager,
        modifier = modifier,
        beyondViewportPageCount = libraryPager.beyondViewportPageCount,
        key = { LibraryTab.entries[it].name },
    ) { index ->
        // A crossfade to a far tab keeps the tabs it passes over in the pager; they compose nothing.
        if (libraryPager.shows(index)) {
            page(LibraryTab.entries[index], with(libraryPager) { Modifier.crossfading(index) })
        }
    }
}

/**
 * Lets horizontal drags on this element that nothing inside it used move [pager] as if they were on
 * the pager itself: they page, or snap back, by the pager's own rules.
 */
@Composable
internal fun Modifier.swipesPages(pager: PagerState): Modifier = scrollableArea(
    state = pager,
    orientation = Orientation.Horizontal,
    overscrollEffect = null,
    flingBehavior = PagerDefaults.flingBehavior(pager),
)

/**
 * Keeps [libraryPager] and the selected [tab] in step both ways: a [tab] chosen elsewhere moves the
 * pager there, and a page the pager settles on that isn't [tab] goes to [onSettle].
 */
@Composable
internal fun SyncLibraryPager(libraryPager: LibraryPager, tab: LibraryTab, onSettle: (LibraryTab) -> Unit) {
    val pager = libraryPager.pager
    LaunchedEffect(pager, tab) {
        if (pager.settledPage != tab.ordinal) pager.scrollToPage(tab.ordinal)
    }
    val currentTab by rememberUpdatedState(tab)
    val currentOnSettle by rememberUpdatedState(onSettle)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val settled = LibraryTab.entries[page]
            if (settled != currentTab) currentOnSettle(settled)
        }
    }
}
