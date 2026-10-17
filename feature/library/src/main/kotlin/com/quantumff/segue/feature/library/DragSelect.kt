package com.quantumff.segue.feature.library

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import kotlinx.coroutines.flow.collectLatest

/**
 * The indices a long-press-and-drag covers (DESIGN.md §4.2): from the long-pressed [anchor] to the
 * item under the finger, both included, in list order.
 */
internal class DragSelectRange(private val anchor: Int) {
    private var end = anchor

    /**
     * Moves the far end to [index]. Returns the indices that came into the range, in list order,
     * and those that left it. The anchor never does either: its long-press already picked or dropped it.
     */
    fun moveTo(index: Int): RangeChange {
        val old = span(end)
        val new = span(index)
        end = index
        return RangeChange(entered = new.filter { it !in old }, left = old.filter { it !in new })
    }

    private fun span(end: Int): IntRange = minOf(anchor, end)..maxOf(anchor, end)
}

internal data class RangeChange(val entered: List<Int>, val left: List<Int>)

/**
 * Long-press-and-drag selection (DESIGN.md §4.2), as in Google Photos. The long-press picks or
 * drops its item as before; dragging on without lifting gives every item between it and the finger
 * the same state, and dragging back returns the items left behind to how they were. Near the
 * list's top or bottom edge the list scrolls on its own.
 */
@Stable
internal class DragSelectState {
    private var range: DragSelectRange? = null

    /** Whether the drag picks (the long-press picked its item) or drops. */
    private var picking = true

    /** The picks when the drag began, which an item the drag leaves behind returns to. */
    private var before: Set<String> = emptySet()

    /** Indices in the range that were placeholders when the drag reached them; each move tries them again. */
    private val unloaded = sortedSetOf<Int>()

    /** The last finger position, in the list's coordinates, so auto-scroll can re-check what is under it. */
    internal var pointer: Offset? = null

    /** List pixels to scroll each frame; negative scrolls up. */
    internal var autoScroll by mutableFloatStateOf(0f)

    val isActive: Boolean get() = range != null

    /** The long-press on [index] began a drag. [picked] are the ids picked before that long-press. */
    fun start(index: Int, id: String, picked: Set<String>) {
        range = DragSelectRange(index)
        picking = id !in picked
        before = picked
        unloaded.clear()
    }

    fun end() {
        range = null
        unloaded.clear()
        pointer = null
        autoScroll = 0f
    }

    /**
     * Moves the drag to [index]: each item that changes, with whether it is now picked. Placeholders
     * the range covers are picked on a later move, once they load.
     */
    internal fun moveTo(index: Int, itemAt: (Int) -> LibraryItem?): List<Pair<LibraryItem, Boolean>> {
        val change = range?.moveTo(index) ?: return emptyList()
        // An item that never loaded was never picked, so leaving it changes nothing.
        val left = change.left.filter { it !in unloaded }.mapNotNull { i -> itemAt(i)?.let { it to (it.id in before) } }
        unloaded -= change.left.toSet()
        unloaded += change.entered
        val entered = unloaded.toList().mapNotNull { i ->
            itemAt(i)?.let {
                unloaded -= i
                it to picking
            }
        }
        return entered + left
    }
}

/**
 * [dragToSelect] for a paged grid of [items], each picked as [toItem] makes it. [startPadding] is
 * the grid's start content padding.
 */
@Composable
internal fun <T : Any> Modifier.dragToSelect(
    state: DragSelectState,
    grid: LazyGridState,
    items: LazyPagingItems<T>,
    toItem: (T) -> LibraryItem,
    onPick: (LibraryItem, Boolean) -> Unit,
    startPadding: Dp,
): Modifier {
    val startPx = with(LocalDensity.current) { startPadding.toPx() }
    return dragToSelect(
        state = state,
        scrollable = grid,
        indexAt = { grid.indexAt(it, startPx) },
        itemAt = { items.peekOrNull(it)?.let(toItem) },
        onPick = onPick,
        bottomInset = LocalShellContentPadding.current.calculateBottomPadding(),
    )
}

/** [dragToSelect] for a paged list of [items], each picked as [toItem] makes it. */
@Composable
internal fun <T : Any> Modifier.dragToSelect(
    state: DragSelectState,
    list: LazyListState,
    items: LazyPagingItems<T>,
    toItem: (T) -> LibraryItem,
    onPick: (LibraryItem, Boolean) -> Unit,
): Modifier = dragToSelect(
    state = state,
    scrollable = list,
    indexAt = { list.indexAt(it) },
    itemAt = { items.peekOrNull(it)?.let(toItem) },
    onPick = onPick,
    bottomInset = LocalShellContentPadding.current.calculateBottomPadding(),
)

/** The loaded item at [index], or null for a placeholder or an index the list no longer has. */
private fun <T : Any> LazyPagingItems<T>.peekOrNull(index: Int): T? =
    if (index in 0 until itemCount) peek(index) else null

/**
 * Follows the finger after a long-press started [state] (see [DragSelectState.start]), picking or
 * dropping what it passes over through [onPick]. Apply to the list itself, so positions are in its
 * coordinates. [indexAt] finds the item under a point; [bottomInset] is the shell chrome drawn over
 * the list's bottom edge, which auto-scroll keeps clear of.
 */
@Composable
private fun Modifier.dragToSelect(
    state: DragSelectState,
    scrollable: ScrollableState,
    indexAt: (Offset) -> Int?,
    itemAt: (Int) -> LibraryItem?,
    onPick: (LibraryItem, Boolean) -> Unit,
    bottomInset: Dp,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val latestIndexAt by rememberUpdatedState(indexAt)
    val latestItemAt by rememberUpdatedState(itemAt)
    val latestOnPick by rememberUpdatedState(onPick)
    val density = LocalDensity.current
    val edgePx = with(density) { AUTO_SCROLL_EDGE.toPx() }
    val maxSpeedPx = with(density) { AUTO_SCROLL_MAX_SPEED.toPx() }
    // The mini player comes and goes during a drag's lifetime; read the inset at each move.
    val insetPx by rememberUpdatedState(with(density) { bottomInset.toPx() })
    val heightPx = remember { floatArrayOf(0f) }

    fun follow(position: Offset) {
        state.pointer = position
        val bottom = heightPx[0] - insetPx
        state.autoScroll = when {
            position.y < edgePx -> -maxSpeedPx * (1f - position.y / edgePx).coerceAtMost(1f)
            position.y > bottom - edgePx -> maxSpeedPx * ((position.y - (bottom - edgePx)) / edgePx).coerceAtMost(1f)
            else -> 0f
        }
        val index = latestIndexAt(position) ?: return
        val picks = state.moveTo(index, latestItemAt)
        picks.forEach { (item, picked) -> latestOnPick(item, picked) }
        if (picks.isNotEmpty()) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LaunchedEffect(state, scrollable) {
        snapshotFlow { state.autoScroll != 0f }.collectLatest { scrolling ->
            while (scrolling) {
                withFrameNanos { }
                scrollable.scrollBy(state.autoScroll)
                state.pointer?.let(::follow)
            }
        }
    }

    return pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                // The initial pass, so a drag that selects never scrolls the list or reaches the items.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                heightPx[0] = size.height.toFloat()
                // A new touch: whatever drag was on is over (a long-press from accessibility has no touch to end it).
                if (event.changes.any { it.changedToDown() }) state.end()
                if (!state.isActive) continue
                val finger = event.changes.firstOrNull { it.pressed }
                if (finger == null) {
                    state.end()
                } else {
                    event.changes.forEach { it.consume() }
                    follow(finger.position)
                }
            }
        }
    }
}

/** The grid item under [point], in the grid's coordinates. Item offsets leave out the content padding. */
internal fun LazyGridState.indexAt(point: Offset, startPaddingPx: Float): Int? {
    val info = layoutInfo
    val x = point.x - startPaddingPx
    val y = point.y - info.beforeContentPadding
    return info.visibleItemsInfo.firstOrNull { item ->
        x >= item.offset.x && x < item.offset.x + item.size.width &&
            y >= item.offset.y && y < item.offset.y + item.size.height
    }?.index
}

/** The list item under [point], in the list's coordinates. */
internal fun LazyListState.indexAt(point: Offset): Int? {
    val info = layoutInfo
    val y = point.y - info.beforeContentPadding
    return info.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }?.index
}

/** How close to the list's top or bottom edge the finger starts auto-scroll, which speeds up toward the edge. */
private val AUTO_SCROLL_EDGE = 64.dp

/** Auto-scroll per frame with the finger at the edge. */
private val AUTO_SCROLL_MAX_SPEED = 16.dp
