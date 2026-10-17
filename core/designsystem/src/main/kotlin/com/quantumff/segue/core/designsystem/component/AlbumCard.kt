package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing

/** UI state for an album (or playlist) card or row. [subtitle] is the artist, or the year on artist pages. */
@Immutable
data class AlbumCardState(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverArtId: String?,
    val download: DownloadBadgeState? = null,
)

/**
 * Feishin "poster" album card (DESIGN.md §5.1): a square of art (radius `md`), then the title and
 * a muted subtitle. No card surface, no padding. Width comes from [modifier] (grid cell or section
 * size); [artDisplaySize] picks the image size bucket and should match that width.
 */
@Composable
fun AlbumCard(
    state: AlbumCardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    artDisplaySize: Dp = SegueSizes.albumCard,
    selected: Boolean = false,
) {
    Column(modifier = modifier.selectedCard(selected).pressableCard(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            CoverArt(
                coverArtId = state.coverArtId,
                displaySize = artDisplaySize,
                shape = SegueShapes.md,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            if (selected) SelectedArtOverlay(shape = SegueShapes.md, modifier = Modifier.matchParentSize())
            state.download?.let {
                DownloadBadge(
                    state = it,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(SegueSpacing.xs),
                )
            }
        }
        Spacer(Modifier.height(SegueSpacing.sm))
        CardText(title = state.title, subtitle = state.subtitle, textAlign = TextAlign.Start)
    }
}

/** Artist variant of the card: circular art and centered text (DESIGN.md §5.1). */
@Immutable
data class ArtistCardState(val id: String, val name: String, val subtitle: String?, val coverArtId: String?)

@Composable
fun ArtistCard(
    state: ArtistCardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    artDisplaySize: Dp = SegueSizes.artistCircle,
    selected: Boolean = false,
) {
    Column(
        modifier = modifier.selectedCard(selected).pressableCard(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            CoverArt(
                coverArtId = state.coverArtId,
                displaySize = artDisplaySize,
                shape = SegueShapes.full,
                placeholderIcon = SegueIcons.Artist,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            if (selected) SelectedArtOverlay(shape = SegueShapes.full, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.height(SegueSpacing.sm))
        CardText(title = state.name, subtitle = state.subtitle, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CardText(title: String, subtitle: String?, textAlign: TextAlign) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        if (subtitle != null) {
            Spacer(Modifier.height(SegueSpacing.xxs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewSegueThemes
@Composable
private fun AlbumCardPreview() {
    SeguePreview {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.gutter),
            modifier = Modifier.padding(SegueSpacing.lg),
        ) {
            AlbumCard(state = PreviewData.album, onClick = {}, modifier = Modifier.width(SegueSizes.albumCard))
            AlbumCard(
                state = PreviewData.album,
                onClick = {},
                modifier = Modifier.width(SegueSizes.albumCard),
                selected = true,
            )
            ArtistCard(state = PreviewData.artist, onClick = {}, modifier = Modifier.width(SegueSizes.artistCircle))
        }
    }
}
