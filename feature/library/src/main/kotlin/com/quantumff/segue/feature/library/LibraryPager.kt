package com.quantumff.segue.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.scrollableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.quantumff.segue.core.designsystem.theme.LocalReduceMotion
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.model.LibraryTab
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
    private val fade = Animatable(1f)

    /** Moves to [tab] after a tap: a glide, or with reduce motion an instant switch that fades in. */
    fun show(tab: LibraryTab) {
        scope.launch {
            if (!reduceMotion) {
                pager.animateScrollToPage(tab.ordinal)
            } else {
                pager.scrollToPage(tab.ordinal)
                fade.snapTo(0f)
                fade.animateTo(1f, SegueMotion.medium())
            }
        }
    }

    /** The pages' alpha: 1, but for the fade-in after a reduce-motion tab switch. Read it in a draw phase. */
    val pageAlpha: Float get() = fade.value
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
