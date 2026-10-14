package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The item a context menu is about: 48 art, title, muted subtitle. */
@Immutable
data class ContextMenuHeader(
    val title: String,
    val subtitle: String?,
    val coverArtId: String?,
    val circularArt: Boolean = false,
)

/** One menu row. [id] identifies the action for the caller; [destructive] items use `error`. */
@Immutable
data class ContextMenuItem(val id: String, val label: String, val icon: ImageVector, val destructive: Boolean = false)

/**
 * Phone context menu (DESIGN.md §5.7): a `ModalBottomSheet` on `surfaceContainerHigh`, top
 * radius `xl`, drag handle, then [ContextMenuContent]. Item order is the caller's (§5.7 lists it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuSheet(
    header: ContextMenuHeader,
    items: ImmutableList<ContextMenuItem>,
    onItemClick: (ContextMenuItem) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = SegueShapes.sheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = SegueSizes.noElevation,
        modifier = modifier,
    ) {
        ContextMenuContent(
            header = header,
            items = items,
            onItemClick = onItemClick,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/** The sheet body without the sheet, so it can be previewed and reused in dropdowns. */
@Composable
fun ContextMenuContent(
    header: ContextMenuHeader,
    items: ImmutableList<ContextMenuItem>,
    onItemClick: (ContextMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                coverArtId = header.coverArtId,
                displaySize = SegueSizes.sheetHeaderArt,
                shape = if (header.circularArt) SegueShapes.full else SegueShapes.sm,
                placeholderIcon = if (header.circularArt) SegueIcons.Artist else SegueIcons.Disc3,
                modifier = Modifier.size(SegueSizes.sheetHeaderArt),
            )
            Spacer(Modifier.width(SegueSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = header.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                header.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        items.forEach { item ->
            ContextMenuRow(item = item, onClick = { onItemClick(item) })
        }
        Spacer(Modifier.height(SegueSpacing.sm))
    }
}

@Composable
private fun ContextMenuRow(item: ContextMenuItem, onClick: () -> Unit) {
    val color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SegueSizes.sheetRow)
            .clickable(onClick = onClick)
            .padding(horizontal = SegueSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (item.destructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SegueSizes.iconSmall),
        )
        Spacer(Modifier.width(SegueSpacing.lg))
        Text(text = item.label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@PreviewSegueThemes
@Composable
private fun ContextMenuContentPreview() {
    SeguePreview {
        ContextMenuContent(
            header = ContextMenuHeader(title = "Nude", subtitle = "Radiohead · In Rainbows", coverArtId = null),
            items = persistentListOf(
                ContextMenuItem(id = "next", label = "Play next", icon = SegueIcons.PlayNext),
                ContextMenuItem(id = "queue", label = "Add to queue", icon = SegueIcons.AddToQueue),
                ContextMenuItem(id = "mix", label = "Instant mix", icon = SegueIcons.Related),
                ContextMenuItem(id = "playlist", label = "Add to playlist", icon = SegueIcons.AddToPlaylist),
                ContextMenuItem(id = "remove", label = "Remove download", icon = SegueIcons.Remove, destructive = true),
            ),
            onItemClick = {},
        )
    }
}
