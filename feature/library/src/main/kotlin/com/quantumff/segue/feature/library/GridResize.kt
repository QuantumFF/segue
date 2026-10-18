package com.quantumff.segue.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.quantumff.segue.core.designsystem.component.pinchToResize
import com.quantumff.segue.core.designsystem.theme.LocalReduceMotion
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.GridSize
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How a [PinchResizableGrid] lays out its cards right now. */
@Stable
internal data class GridLayout(
    /** The columns; while zooming, cards of the zoomed width, which need not fill the row. */
    val cells: GridCells,
    /**
     * The width of a card that fills the row at rest, for the art's image size. It holds while the
     * fingers are down, so a pinch requests no art until it is released.
     */
    val cellWidth: Dp,
    /** Glides the cards to their new places when the column count changes; see [reflow]. */
    val zoom: GridZoom,
) {
    /** The cards [SegueSpacing.gutter] apart and centred, so a zoomed row grows and shrinks about the middle. */
    val arrangement: Arrangement.Horizontal get() = CenteredCells
}

/**
 * A Library grid whose card [size] a pinch zooms (DESIGN.md §4.2). At [GridSize.Default] it has the
 * columns `GridCells.Adaptive([cell])` would give; it zooms between one column and [MAX_COLUMNS]
 * (or the usual count, when more fit). While pinching, the cards follow the fingers' spread and the
 * row under them stays under them; when the cards come nearer another column count's width the
 * grid reflows, and the cards glide to their new places. On release the cards spring to fill the
 * row, and the column count is handed to [onResize] to keep.
 *
 * @param horizontalPadding the grid's side content padding, on each side.
 * @param state the grid's scroll state, moved to keep the pinched row under the fingers.
 */
@Composable
internal fun PinchResizableGrid(
    size: GridSize,
    cell: Dp,
    horizontalPadding: Dp,
    state: LazyGridState,
    onResize: (GridSize) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (GridLayout) -> Unit,
) {
    // The kept size, shown until the stored size catches up so the cards do not jump back meanwhile.
    var pending by remember { mutableStateOf<GridSize?>(null) }
    LaunchedEffect(size, pending) { if (size == pending) pending = null }
    val shown = pending ?: size
    val scope = rememberCoroutineScope()
    val zoom = remember(state) { GridZoom(state, scope) }
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth - horizontalPadding * 2
        val usual = usualColumns(width, cell, SegueSpacing.gutter)
        val columns = gridColumns(width, cell, SegueSpacing.gutter, shown)
        SideEffect {
            with(density) {
                zoom.width = width.toPx()
                zoom.gutter = SegueSpacing.gutter.toPx()
            }
            zoom.maxColumns = maxColumns(usual)
            zoom.restColumns = columns
            zoom.reduceMotion = reduceMotion
            zoom.layoutDirection = layoutDirection
            zoom.onKeep = { kept ->
                val to = GridSize(kept - usual)
                pending = to
                onResize(to)
            }
        }
        val live = zoom.live
        val layout = GridLayout(
            cells = if (live == null) GridCells.Fixed(columns) else ZoomedCells(live.columns, live.cellPx),
            cellWidth = (width - SegueSpacing.gutter * (columns - 1)) / columns,
            zoom = zoom,
        )
        Box(
            Modifier
                .fillMaxSize()
                .pinchToResize(
                    onPinch = zoom::pinch,
                    onPinchEnd = zoom::release,
                    onGrow = { zoom.step(columns - 1) },
                    onShrink = { zoom.step(columns + 1) },
                ),
        ) {
            content(layout)
        }
    }
}

/**
 * Columns for [size] cards across [width]: at [GridSize.Default], as many [cell]-wide cards as fit
 * with [gutter] between them (as `GridCells.Adaptive`), plus the size's extra columns. Never fewer
 * than one, nor more than [MAX_COLUMNS] unless the usual count is already more.
 */
internal fun gridColumns(width: Dp, cell: Dp, gutter: Dp, size: GridSize): Int {
    val usual = usualColumns(width, cell, gutter)
    return (usual + size.extraColumns).coerceIn(1, maxColumns(usual))
}

private fun usualColumns(width: Dp, cell: Dp, gutter: Dp): Int =
    ((width + gutter) / (cell + gutter)).toInt().coerceAtLeast(1)

private fun maxColumns(usual: Int) = maxOf(MAX_COLUMNS, usual)

/**
 * A card's place while the grid reflows: it starts where the earlier column count put it and
 * glides to its place in the grid. Read in the placement phase, so gliding recomposes nothing.
 */
internal fun Modifier.reflow(zoom: GridZoom, index: Int): Modifier = offset { zoom.reflowOffset(index) }

/** A zoomed grid's live column count and card width, in pixels. */
@Immutable
internal data class ZoomedGrid(val columns: Int, val cellPx: Float)

/**
 * The zoom of one [PinchResizableGrid]: the live card width while pinching or settling, the row
 * held under the fingers, and the glide between column counts.
 */
@Stable
internal class GridZoom(private val grid: LazyGridState, private val scope: CoroutineScope) {
    // Set by each composition of the grid.
    var width = 0f
    var gutter = 0f
    var maxColumns = 1
    var restColumns = 1
    var reduceMotion = false
    var layoutDirection = LayoutDirection.Ltr
    var onKeep: (columns: Int) -> Unit = {}

    /** The live zoom while pinching or settling; null at rest, where the cards fill [restColumns]. */
    var live by mutableStateOf<ZoomedGrid?>(null)
        private set

    private var pinching = false
    private var settling: Job? = null

    // The card held under the fingers: its index, where they touched it as a fraction of its row's
    // height, and where they are now, in the grid's pixels. -1 when nothing is held.
    private var anchor = -1
    private var anchorFraction = 0f
    private var anchorY = 0f

    /** A row's height beyond its card width: the card's text and the gap to the next row. */
    private var rowExtra = 0f

    // The glide between column counts: the weight each earlier count had when it began, and
    // the card whose row stays put in every count.
    private val glide = Animatable(1f)
    private var glideFrom: Map<Int, Float> = emptyMap()
    private var glideAnchor = 0

    /** The width of a card when [columns] fill the row. */
    fun cellWidth(columns: Int): Float = (width - gutter * (columns - 1)) / columns

    fun pinch(centroid: Offset, pan: Offset, zoom: Float): Boolean {
        val start = live ?: ZoomedGrid(restColumns, cellWidth(restColumns))
        if (!pinching) {
            pinching = true
            settling?.cancel()
            hold(centroid)
        } else {
            anchorY += pan.y
        }
        val cellPx = (start.cellPx * zoom).coerceIn(cellWidth(maxColumns), cellWidth(1))
        val columns = columnsFor(cellPx, start.columns)
        show(columns, cellPx)
        return columns != start.columns
    }

    fun release() {
        pinching = false
        val columns = live?.columns ?: return
        if (columns != restColumns) onKeep(columns)
        settle(columns)
    }

    /** An accessibility step to [columns]: kept at once, then zoomed to as a pinch would. */
    fun step(columns: Int): Boolean {
        if (pinching || columns !in 1..maxColumns || columns == restColumns) return false
        anchor = -1
        measureRowExtra()?.let { rowExtra = it }
        onKeep(columns)
        settle(columns)
        return true
    }

    /** Springs the cards to fill [columns], reflowing on the way if need be, then rests. */
    private fun settle(columns: Int) {
        settling?.cancel()
        settling = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val from = live?.cellPx ?: cellWidth(restColumns)
            if (!reduceMotion) {
                animate(from, cellWidth(columns), animationSpec = SegueMotion.player()) { cellPx, _ ->
                    show(columnsFor(cellPx, live?.columns ?: restColumns), cellPx)
                }
            }
            show(columns, cellWidth(columns))
            live = null
            anchor = -1
        }
    }

    private fun show(columns: Int, cellPx: Float) {
        val before = live?.columns ?: restColumns
        if (columns != before) reflow(before, columns)
        live = ZoomedGrid(columns, cellPx)
        holdAnchor(columns, cellPx)
    }

    /**
     * The column count whose filling card width is nearest [cellPx], in proportion. It stays at
     * [current] until another is clearly nearer, so fingers resting between two do not flicker.
     */
    private fun columnsFor(cellPx: Float, current: Int): Int {
        fun distance(columns: Int) = abs(ln(cellPx / cellWidth(columns)))
        val nearest = (1..maxColumns).minBy(::distance)
        return if (distance(current) - distance(nearest) > REFLOW_HYSTERESIS) nearest else current
    }

    /** Holds the card under [centroid] there, and measures its row. */
    private fun hold(centroid: Offset) {
        val info = grid.layoutInfo
        val y = centroid.y - info.beforeContentPadding
        val rows = info.visibleItemsInfo.groupBy { it.row }.values
        val row =
            rows.firstOrNull { it.first().offset.y + it.rowPitch(info.mainAxisItemSpacing) > y } ?: rows.lastOrNull()
        if (row == null) {
            anchor = -1
            return
        }
        // The card in the column under the fingers; only its row is held, but the glide keeps its column too.
        val fromLeft = (centroid.x / info.viewportSize.width).coerceIn(0f, 1f)
        val across = if (layoutDirection == LayoutDirection.Ltr) fromLeft else 1 - fromLeft
        val item = row.sortedBy { it.column }.let { it[(across * it.size).toInt().coerceAtMost(it.lastIndex)] }
        val pitch = row.rowPitch(info.mainAxisItemSpacing)
        anchor = item.index
        anchorFraction = ((y - item.offset.y) / pitch).coerceIn(0f, 1f)
        anchorY = centroid.y
        rowExtra = pitch - item.size.width
    }

    /** Scrolls so the held card's row stays under the fingers, as far as the list allows. */
    private fun holdAnchor(columns: Int, cellPx: Float) {
        if (anchor < 0) return
        val pitch = cellPx + rowExtra
        val row = anchor / columns
        // Where the held row starts, in the grid's content coordinates (0 is below the top padding).
        val top = anchorY - anchorFraction * pitch - grid.layoutInfo.beforeContentPadding
        val rowsAbove = ceil(top / pitch).toInt().coerceIn(0, row)
        grid.requestScrollToItem(
            index = (row - rowsAbove) * columns,
            scrollOffset = (rowsAbove * pitch - top).roundToInt().coerceAtLeast(0),
        )
    }

    private fun measureRowExtra(): Float? {
        val info = grid.layoutInfo
        val row = info.visibleItemsInfo.groupBy { it.row }.values.firstOrNull() ?: return null
        return row.rowPitch(info.mainAxisItemSpacing) - row.first().size.width
    }

    /** Starts the cards gliding from where [before] columns put them, or from mid-glide, to [after] columns. */
    private fun reflow(before: Int, after: Int) {
        if (reduceMotion) return
        val progress = glide.value
        glideFrom = buildMap {
            glideFrom.forEach { (columns, weight) -> put(columns, weight * (1 - progress)) }
            merge(before, progress, Float::plus)
        }.filterValues { it > MIN_GLIDE_WEIGHT }
        glideAnchor = if (anchor >= 0) anchor else grid.firstVisibleItemIndex
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            glide.snapTo(0f)
            glide.animateTo(1f, SegueMotion.player())
        }
    }

    fun reflowOffset(index: Int): IntOffset {
        val remaining = 1 - glide.value
        if (remaining == 0f || glideFrom.isEmpty()) return IntOffset.Zero
        val columns = live?.columns ?: restColumns
        val cellPx = live?.cellPx ?: cellWidth(restColumns)
        var x = 0f
        var y = 0f
        glideFrom.forEach { (from, weight) ->
            x += weight * (cardX(from, index, cellPx) - cardX(columns, index, cellPx))
            y += weight * (cardY(from, index, cellPx) - cardY(columns, index, cellPx))
        }
        return IntOffset((x * remaining).roundToInt(), (y * remaining).roundToInt())
    }

    /** Where card [index] starts across a row of [columns] cards [cellPx] wide, centred. */
    private fun cardX(columns: Int, index: Int, cellPx: Float): Float =
        (width - columns * cellPx - (columns - 1) * gutter) / 2 + (index % columns) * (cellPx + gutter)

    /** Where card [index]'s row starts in [columns] columns, from the glide anchor's row. */
    private fun cardY(columns: Int, index: Int, cellPx: Float): Float =
        (index / columns - glideAnchor / columns) * (cellPx + rowExtra)
}

/** From the top of this row of cards to the top of the next. */
private fun List<LazyGridItemInfo>.rowPitch(spacing: Int): Float = maxOf { it.size.height }.toFloat() + spacing

/** [columns] cards [cellPx] wide: a zoomed grid's cells, which need not fill the row. */
private data class ZoomedCells(val columns: Int, val cellPx: Float) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> =
        List(columns) { cellPx.toInt() }
}

/**
 * Cards [SegueSpacing.gutter] apart, centred, and overflowing both sides alike when they do not
 * fit; `Arrangement.spacedBy` would squeeze them in.
 */
private object CenteredCells : Arrangement.Horizontal {
    override val spacing = SegueSpacing.gutter

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) {
        val gap = spacing.roundToPx()
        var start = (totalSize - sizes.sum() - gap * (sizes.size - 1).coerceAtLeast(0)) / 2
        sizes.forEachIndexed { index, size ->
            outPositions[index] = if (layoutDirection == LayoutDirection.Ltr) start else totalSize - start - size
            start += size + gap
        }
    }
}

/** The most columns a pinch zooms out to, unless the screen already fits more. */
private const val MAX_COLUMNS = 8

/** How much nearer, in proportion, another column count's card width must be before the grid reflows. */
private const val REFLOW_HYSTERESIS = 0.04f

/** An earlier column count with less weight than this no longer adds to the glide. */
private const val MIN_GLIDE_WEIGHT = 0.01f
