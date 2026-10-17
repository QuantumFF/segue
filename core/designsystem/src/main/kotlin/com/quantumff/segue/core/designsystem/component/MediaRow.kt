package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing

/**
 * A 64-tall list row with 48 art: albums in list view, artists (circular art), playlists
 * (DESIGN.md §4.2). The row is the touch target.
 */
@Composable
fun MediaRow(
    title: String,
    subtitle: String?,
    coverArtId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    artShape: Shape = SegueShapes.sm,
    placeholderIcon: ImageVector = SegueIcons.Disc3,
    download: DownloadBadgeState? = null,
    selected: Boolean = false,
    onLongClickLabel: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SegueSizes.mediaRow)
            .selectedRow(selected)
            .rowClickable(enabled = true, onClick, onLongClick, onLongClickLabel)
            .padding(start = SegueSpacing.lg, end = if (onMoreClick == null) SegueSpacing.lg else SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CoverArt(
                coverArtId = coverArtId,
                displaySize = SegueSizes.mediaRowArt,
                shape = artShape,
                placeholderIcon = placeholderIcon,
                modifier = Modifier.size(SegueSizes.mediaRowArt),
            )
            if (selected) SelectedArtOverlay(shape = artShape, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.width(SegueSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (download != null) {
            Spacer(Modifier.width(SegueSpacing.sm))
            DownloadBadge(download)
        }
        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(SegueSizes.overflowButton)) {
                Icon(
                    imageVector = SegueIcons.MoreVertical,
                    contentDescription = stringResource(R.string.ds_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SegueSizes.iconSmall),
                )
            }
        }
    }
}

@PreviewSegueThemes
@Composable
private fun MediaRowPreview() {
    SeguePreview {
        Column {
            MediaRow(
                title = PreviewData.album.title,
                subtitle = PreviewData.album.subtitle,
                coverArtId = null,
                onClick = {},
                onMoreClick = {},
                download = DownloadBadgeState.Done,
            )
            MediaRow(
                title = PreviewData.album.title,
                subtitle = PreviewData.album.subtitle,
                coverArtId = null,
                onClick = {},
                selected = true,
            )
            MediaRow(
                title = PreviewData.artist.name,
                subtitle = PreviewData.artist.subtitle,
                coverArtId = null,
                onClick = {},
                artShape = SegueShapes.full,
                placeholderIcon = SegueIcons.Artist,
            )
        }
    }
}
