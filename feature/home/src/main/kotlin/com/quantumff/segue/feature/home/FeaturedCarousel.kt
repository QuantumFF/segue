package com.quantumff.segue.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.art.ArtNoise
import com.quantumff.segue.core.designsystem.art.BlurredArt
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.component.PlayButton
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShadows
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.designsystem.theme.tabularNumbers
import com.quantumff.segue.core.model.Album
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Feishin feature carousel (DESIGN.md §4.1): a pager of random albums on pre-blurred art, 200
 * tall, page spacing 12, the next card peeking 16. No auto-advance. Pager dots below.
 */
@Composable
internal fun FeaturedCarousel(
    albums: ImmutableList<Album>,
    onAlbumClick: (Album) -> Unit,
    onPlayClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { albums.size }
    Column(modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            key = { albums[it].id },
            contentPadding = PaddingValues(horizontal = SegueSpacing.lg + SegueSpacing.gutter),
            pageSpacing = SegueSpacing.gutter,
            beyondViewportPageCount = 1,
        ) { page ->
            FeaturedCard(album = albums[page], onClick = { onAlbumClick(albums[page]) }, onPlayClick = {
                onPlayClick(albums[page])
            })
        }
        Spacer(Modifier.height(SegueSpacing.sm))
        PagerDots(
            count = albums.size,
            current = { pagerState.currentPage },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun FeaturedCard(album: Album, onClick: () -> Unit, onPlayClick: () -> Unit) {
    val onArt = SegueTheme.colors.onArt
    val onArtMuted = SegueTheme.colors.onArtMuted
    val artShadow = SegueTheme.colors.shadow.copy(alpha = SegueShadows.FEATURE_CAROUSEL_ART_ALPHA)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .clip(SegueShapes.md)
            // Black under the art in every theme: the card's text is always light (onArt).
            .background(Color.Black)
            .clickable(onClick = onClick),
    ) {
        BlurredArt(coverArtId = album.coverArtId, alpha = BACKDROP_ALPHA, modifier = Modifier.matchParentSize())
        Spacer(
            Modifier
                .matchParentSize()
                .drawBehind {
                    // A light veil at the top too, so white text stays readable over pale art.
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = TOP_SHADE_ALPHA), Color.Black.copy(alpha = SHADE_ALPHA)),
                        ),
                    )
                },
        )
        ArtNoise(Modifier.matchParentSize())
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(SegueSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                coverArtId = album.coverArtId,
                displaySize = ART_SIZE,
                shape = SegueShapes.md,
                crossfade = false,
                modifier = Modifier
                    .size(ART_SIZE)
                    .shadow(
                        SegueShadows.featureCarouselArt,
                        SegueShapes.md,
                        ambientColor = artShadow,
                        spotColor = artShadow,
                    ),
            )
            Spacer(Modifier.width(SegueSpacing.lg))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SegueSpacing.xxs)) {
                Text(
                    text = stringResource(R.string.feature_home_featured).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onArtMuted,
                )
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = onArt,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                album.artist?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onArtMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = listOfNotNull(
                        album.year?.toString(),
                        pluralStringResource(R.plurals.feature_home_track_count, album.songCount, album.songCount),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = onArtMuted,
                    maxLines = 1,
                )
            }
        }
        val playLabel = stringResource(R.string.feature_home_play_album, album.name)
        PlayButton(
            onClick = onPlayClick,
            size = SegueSizes.touchTarget,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(SegueSpacing.md)
                .semantics { contentDescription = playLabel },
        )
    }
}

@Composable
private fun PagerDots(count: Int, current: () -> Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.feature_home_featured_page, current() + 1, count)
    Row(
        modifier = modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(SegueSpacing.xs + SegueSpacing.xxs),
    ) {
        val selected = MaterialTheme.colorScheme.onSurface
        val unselected = MaterialTheme.colorScheme.outline
        repeat(count) { index ->
            Box(
                Modifier
                    .size(DOT_SIZE)
                    .drawBehind { drawCircle(if (index == current()) selected else unselected) },
            )
        }
    }
}

private val CARD_HEIGHT = 200.dp
private val ART_SIZE = 120.dp
private val DOT_SIZE = 6.dp
private const val BACKDROP_ALPHA = 0.8f
private const val SHADE_ALPHA = 0.6f
private const val TOP_SHADE_ALPHA = 0.25f

@PreviewSegueThemes
@Composable
private fun FeaturedCarouselPreview() {
    SeguePreview {
        FeaturedCarousel(
            albums = persistentListOf(PreviewAlbums.first(), PreviewAlbums.last()),
            onAlbumClick = {},
            onPlayClick = {},
        )
    }
}
