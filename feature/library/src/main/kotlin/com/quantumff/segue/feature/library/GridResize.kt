package com.quantumff.segue.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.quantumff.segue.core.designsystem.component.pinchToResize
import com.quantumff.segue.core.designsystem.theme.LocalReduceMotion
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.GridSize
import kotlinx.coroutines.delay

/** How a [PinchResizableGrid] lays out its cards right now. */
@Immutable
internal data class GridLayout(
    val columns: Int,
    /** One card's width, for the art's image size. */
    val cellWidth: Dp,
    /** True for a moment after the column count changed, so the cards move to their new places. */
    val animatePlacement: Boolean,
)

/**
 * A Library grid whose card [size] a pinch steps (DESIGN.md §4.2). At [GridSize.Medium] it has the
 * columns `GridCells.Adaptive([cell])` would give; each step adds or drops one. The new size shows
 * at once and is handed to [onResize] to keep. A step that would not change the column count (one
 * column already) is refused, so the pinch gives no haptic for it.
 *
 * @param horizontalPadding the grid's side content padding, on each side.
 */
@Composable
internal fun PinchResizableGrid(
    size: GridSize,
    cell: Dp,
    horizontalPadding: Dp,
    onResize: (GridSize) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (GridLayout) -> Unit,
) {
    // Shown before the stored size catches up, so each step lands under the fingers at once.
    var shown by remember(size) { mutableStateOf(size) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth - horizontalPadding * 2
        val columns = gridColumns(width, cell, SegueSpacing.gutter, shown)
        fun step(to: GridSize?): Boolean {
            if (to == null || gridColumns(width, cell, SegueSpacing.gutter, to) == columns) return false
            shown = to
            onResize(to)
            return true
        }
        var settled by remember { mutableIntStateOf(columns) }
        LaunchedEffect(columns) {
            delay(PLACEMENT_SETTLE_MILLIS)
            settled = columns
        }
        val layout = GridLayout(
            columns = columns,
            cellWidth = (width - SegueSpacing.gutter * (columns - 1)) / columns,
            animatePlacement = columns != settled && !LocalReduceMotion.current,
        )
        Box(
            Modifier
                .fillMaxSize()
                .pinchToResize(onGrow = { step(shown.larger) }, onShrink = { step(shown.smaller) }),
        ) {
            content(layout)
        }
    }
}

/**
 * Columns for [size] cards across [width]: at [GridSize.Medium], as many [cell]-wide cards as fit
 * with [gutter] between them (as `GridCells.Adaptive`); each size smaller adds a column, each
 * larger drops one, down to one.
 */
internal fun gridColumns(width: Dp, cell: Dp, gutter: Dp, size: GridSize): Int {
    val usual = ((width + gutter) / (cell + gutter)).toInt().coerceAtLeast(1)
    return (usual + GridSize.Medium.ordinal - size.ordinal).coerceAtLeast(1)
}

/** Long enough for the `player` spring to settle the moved cards. */
private const val PLACEMENT_SETTLE_MILLIS = 600L
