package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.quantumff.segue.core.designsystem.art.ArtNoise
import com.quantumff.segue.core.designsystem.art.BlurredArt
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.component.TrackSwipePager
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueAlpha
import com.quantumff.segue.core.designsystem.theme.SegueShadows
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.feature.nowplaying.AdjacentArt
import com.quantumff.segue.feature.nowplaying.NowPlayingTrack
import com.quantumff.segue.feature.nowplaying.R

/**
 * "Blurred art" background (DESIGN.md §4.7): the pre-blurred bitmap scaled to cover, a gradient
 * from black 30% at the top to black 85% at the bottom, and noise. No live blur.
 */
@Composable
internal fun NowPlayingBackground(coverArtId: String?, modifier: Modifier = Modifier) {
    val shade = SegueTheme.colors.artShade
    val gradient = remember(shade) {
        Brush.verticalGradient(
            listOf(
                shade.copy(alpha = SegueAlpha.NOW_PLAYING_SHADE_TOP),
                shade.copy(alpha = SegueAlpha.NOW_PLAYING_SHADE_BOTTOM),
            ),
        )
    }
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        BlurredArt(coverArtId = coverArtId, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(gradient),
        )
        ArtNoise(Modifier.fillMaxSize())
    }
}

/**
 * The square art, as large as fits (at most [SegueSizes.nowPlayingArtMax]), radius `lg` with the
 * Now Playing shadow. Swiping it pages to the previous or next track like the mini player; tap
 * opens lyrics, long-press opens the track menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtPager(
    track: NowPlayingTrack,
    previous: AdjacentArt?,
    next: AdjacentArt?,
    onSkipTo: (Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(min(maxWidth - SegueSpacing.xl * 2, maxHeight - SegueSpacing.xl), SegueSizes.nowPlayingArtMax)
        val current = AdjacentArt(track.coverArtId, track.timelineIndex)
        TrackSwipePager(
            current = current,
            currentKey = track.timelineIndex,
            previous = previous,
            next = next,
            onSwipeToPrevious = { previous?.let { onSkipTo(it.timelineIndex) } },
            onSwipeToNext = { next?.let { onSkipTo(it.timelineIndex) } },
            modifier = Modifier.fillMaxSize(),
        ) { art, pageModifier ->
            Box(pageModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val shadowColor = SegueTheme.colors.shadow.copy(alpha = SegueShadows.NOW_PLAYING_ART_ALPHA)
                CoverArt(
                    coverArtId = art.coverArtId,
                    displaySize = SegueSizes.nowPlayingArtMax,
                    shape = SegueShapes.lg,
                    crossfade = false,
                    modifier = Modifier
                        .size(side)
                        .shadow(
                            elevation = SegueShadows.nowPlayingArt,
                            shape = SegueShapes.lg,
                            ambientColor = shadowColor,
                            spotColor = shadowColor,
                        )
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                )
            }
        }
    }
}

/**
 * Title (22sp, marquee while resumed), then the artists and album as links at 70%, with the
 * favorite heart on the right.
 */
@Composable
internal fun TrackMetadata(
    track: NowPlayingTrack,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onArt = SegueTheme.colors.onArt
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = TITLE_SIZE, lineHeight = TITLE_LINE),
                color = onArt,
                maxLines = 1,
                overflow = if (resumed) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (resumed) Modifier.basicMarquee() else Modifier,
            )
            Text(
                text = rememberArtistAlbumLinks(track, onArtistClick, onAlbumClick),
                style = MaterialTheme.typography.bodyLarge,
                color = SegueTheme.colors.onArtMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onFavoriteClick, modifier = Modifier.size(SegueSizes.touchTarget)) {
            Icon(
                imageVector = if (track.isStarred) SegueIcons.HeartFilled else SegueIcons.Heart,
                contentDescription = stringResource(
                    if (track.isStarred) {
                        R.string.feature_nowplaying_unfavorite
                    } else {
                        R.string.feature_nowplaying_favorite
                    },
                ),
                tint = onArt,
            )
        }
    }
}

/** "Artist A, Artist B · Album", each name a link when its ID is known. */
@Composable
private fun rememberArtistAlbumLinks(
    track: NowPlayingTrack,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
) = remember(track.artists, track.artist, track.album, track.albumId, onArtistClick, onAlbumClick) {
    val linkStyle = TextLinkStyles(SpanStyle())
    buildAnnotatedString {
        if (track.artists.isNotEmpty()) {
            track.artists.forEachIndexed { index, artist ->
                if (index > 0) append(", ")
                withLink(LinkAnnotation.Clickable("artist:${artist.id}", linkStyle) { onArtistClick(artist.id) }) {
                    append(artist.name)
                }
            }
        } else {
            track.artist?.let(::append)
        }
        val album = track.album
        if (album != null) {
            if (length > 0) append(" · ")
            val albumId = track.albumId
            if (albumId == null) {
                append(album)
            } else {
                withLink(LinkAnnotation.Clickable("album:$albumId", linkStyle) { onAlbumClick(albumId) }) {
                    append(album)
                }
            }
        }
    }
}

/**
 * The header that stays while a panel replaces the art (DESIGN.md §4.7): 48 art, title and
 * artist. Tapping it returns to the art.
 */
@Composable
internal fun CompactTrackHeader(track: NowPlayingTrack, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val backLabel = stringResource(R.string.feature_nowplaying_back_to_art)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClickLabel = backLabel, onClick = onClick)
            .padding(horizontal = SegueSpacing.xl, vertical = SegueSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            coverArtId = track.coverArtId,
            displaySize = SegueSizes.sheetHeaderArt,
            shape = SegueShapes.sm,
            modifier = Modifier.size(SegueSizes.sheetHeaderArt),
        )
        Spacer(Modifier.width(SegueSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = SegueTheme.colors.onArt,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = SegueTheme.colors.onArtMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val TITLE_SIZE = 22.sp
private val TITLE_LINE = 28.sp
