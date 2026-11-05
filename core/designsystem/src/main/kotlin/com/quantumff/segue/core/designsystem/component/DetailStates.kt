package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing

/**
 * An album, artist or playlist page before it has content (DESIGN.md §5.8): [content], such as an
 * [ErrorState] or a not-found [EmptyState], fills the screen under a back bar. The hero shows only
 * once there is content, in a [HeroDetailLayout].
 */
@Composable
fun DetailStateLayout(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        content()
        DetailTopBar(title = "", collapsed = false, onBackClick = onBackClick)
    }
}

/**
 * A detail page loading with nothing cached: after 150ms, track-row skeletons under a back bar,
 * with a [HeroHeaderSkeleton] above them when [showHero]. No action buttons show: nothing could
 * play yet.
 */
@Composable
fun DetailLoadingSkeleton(onBackClick: () -> Unit, modifier: Modifier = Modifier, showHero: Boolean = false) {
    val showSkeleton = rememberDelayedVisibility()
    DetailStateLayout(onBackClick, modifier) {
        if (showSkeleton) {
            SkeletonPulse(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    if (showHero) {
                        HeroHeaderSkeleton()
                    } else {
                        Spacer(Modifier.height(SegueSizes.topBar * 2))
                    }
                    repeat(SKELETON_ROWS) { TrackRowSkeleton() }
                }
            }
        }
    }
}

/**
 * Skeleton for [HeroHeader], using its layout: the art, title and subtitle as blocks, without the
 * action row. Place inside [SkeletonPulse].
 */
@Composable
fun HeroHeaderSkeleton(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = SegueSizes.topBar, bottom = SegueSpacing.lg),
    ) {
        if (maxWidth >= SegueSizes.heroWideBreakpoint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SegueSizes.heroWideMinHeight - SegueSizes.topBar)
                    .padding(horizontal = SegueSpacing.screenMarginWide),
                verticalAlignment = Alignment.Bottom,
            ) {
                SkeletonBlock(Modifier.size(SegueSizes.heroArtWide), shape = SegueShapes.md)
                Spacer(Modifier.width(SegueSpacing.xl))
                Column(Modifier.weight(1f)) { HeroTextSkeleton(Alignment.Start) }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SegueSpacing.screenMarginCompact),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SkeletonBlock(Modifier.size(SegueSizes.heroArtCompact), shape = SegueShapes.md)
                Spacer(Modifier.height(SegueSpacing.lg))
                HeroTextSkeleton(Alignment.CenterHorizontally)
            }
        }
    }
}

@Composable
private fun HeroTextSkeleton(alignment: Alignment.Horizontal) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth(HERO_OVERLINE_FRACTION).height(SegueSpacing.md))
        SkeletonBlock(Modifier.fillMaxWidth(HERO_TITLE_FRACTION).height(SegueSpacing.xxl))
        SkeletonBlock(Modifier.fillMaxWidth(HERO_METADATA_FRACTION).height(SegueSpacing.md))
    }
}

private const val SKELETON_ROWS = 8
private const val HERO_OVERLINE_FRACTION = 0.2f
private const val HERO_TITLE_FRACTION = 0.6f
private const val HERO_METADATA_FRACTION = 0.4f

@PreviewSegueThemes
@Composable
private fun DetailLoadingSkeletonPreview() {
    SeguePreview { DetailLoadingSkeleton(onBackClick = {}, showHero = true) }
}

@PreviewSegueThemes
@Composable
private fun DetailStateLayoutPreview() {
    SeguePreview {
        DetailStateLayout(onBackClick = {}) {
            EmptyState(icon = SegueIcons.Playlists, title = "Playlist not found", message = "It may have been deleted.")
        }
    }
}
