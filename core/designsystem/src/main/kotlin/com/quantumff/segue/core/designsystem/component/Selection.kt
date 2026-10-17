package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueAlpha
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes

/**
 * Multi-select's mark over a selected item's art (DESIGN.md §5.10): `scrim` over the art in its
 * [shape], and a 24 `primary` circle with an `onPrimary` check in the middle. Size it to the art.
 */
@Composable
internal fun SelectedArtOverlay(shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.scrim, shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SegueSizes.icon)
                .background(MaterialTheme.colorScheme.primary, SegueShapes.full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SegueIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(SegueSizes.badge),
            )
        }
    }
}

/** A selected row's `primary` @ 16% fill, and its selected state for accessibility. */
@Composable
internal fun Modifier.selectedRow(selected: Boolean): Modifier = if (selected) {
    semantics { this.selected = true }
        .background(MaterialTheme.colorScheme.primary.copy(alpha = SegueAlpha.SELECTED))
} else {
    this
}

/** A selected card's state for accessibility; the art carries the visible mark. */
internal fun Modifier.selectedCard(selected: Boolean): Modifier =
    if (selected) semantics { this.selected = true } else this
