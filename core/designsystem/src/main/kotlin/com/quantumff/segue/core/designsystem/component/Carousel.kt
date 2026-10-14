package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import kotlinx.collections.immutable.ImmutableList

/**
 * Horizontal carousel (DESIGN.md §4.1): a `LazyRow` with content padding 16, spacing 12, snap
 * fling, and an optional trailing "See all" tile the size of one item.
 */
@Composable
fun <T : Any> HorizontalCarousel(
    items: ImmutableList<T>,
    key: (T) -> Any,
    itemWidth: Dp,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null,
    contentType: String = "carouselItem",
    itemContent: @Composable (T) -> Unit,
) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = SegueSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(SegueSpacing.gutter),
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
    ) {
        items(items = items, key = key, contentType = { contentType }) { item -> itemContent(item) }
        if (onSeeAllClick != null) {
            item(key = SEE_ALL_KEY, contentType = SEE_ALL_KEY) {
                SeeAllTile(onClick = onSeeAllClick, modifier = Modifier.width(itemWidth))
            }
        }
    }
}

/** A section header plus a carousel of album cards: the Home section building block. */
@Composable
fun AlbumCarouselSection(
    title: String,
    albums: ImmutableList<AlbumCardState>,
    onAlbumClick: (AlbumCardState) -> Unit,
    modifier: Modifier = Modifier,
    onAlbumLongClick: ((AlbumCardState) -> Unit)? = null,
    onSeeAllClick: (() -> Unit)? = null,
    cardWidth: Dp = SegueSizes.albumCard,
) {
    Column(modifier) {
        SectionHeader(title = title, onSeeAllClick = onSeeAllClick)
        HorizontalCarousel(
            items = albums,
            key = { it.id },
            itemWidth = cardWidth,
            onSeeAllClick = onSeeAllClick,
            contentType = "albumCard",
        ) { album ->
            AlbumCard(
                state = album,
                onClick = { onAlbumClick(album) },
                onLongClick = onAlbumLongClick?.let { { it(album) } },
                artDisplaySize = cardWidth,
                modifier = Modifier.width(cardWidth),
            )
        }
    }
}

@Composable
private fun SeeAllTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(SegueShapes.md)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = SegueIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SegueSizes.icon),
                )
                Spacer(Modifier.height(SegueSpacing.xs))
                Text(
                    text = stringResource(R.string.ds_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val SEE_ALL_KEY = "seeAll"

@PreviewSegueThemes
@Composable
private fun AlbumCarouselSectionPreview() {
    SeguePreview {
        AlbumCarouselSection(
            title = "Recently played",
            albums = PreviewData.albums,
            onAlbumClick = {},
            onSeeAllClick = {},
        )
    }
}
