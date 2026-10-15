package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes

/**
 * Album, artist and playlist detail layout (DESIGN.md §4.3): a [HeroHeader] as the first list
 * item and a [DetailTopBar] overlaid on top. The bar starts transparent and collapses (fades to the
 * tint, shows the title and a play button) once the hero title scrolls under it.
 * Bottom padding includes [LocalShellContentPadding].
 */
@Composable
fun HeroDetailLayout(
    hero: HeroHeaderState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    heroStyle: HeroStyle = HeroStyle.Tint,
    onPlayClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onSubtitleClick: (() -> Unit)? = null,
    heroActions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    var titleBottom by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // The bar's bottom edge: status bar plus the 56 bar.
    val topBarPx = WindowInsets.statusBars.getTop(density) + with(density) { SegueSizes.topBar.roundToPx() }
    val collapsed by remember(topBarPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                (titleBottom > 0 && listState.firstVisibleItemScrollOffset > titleBottom - topBarPx)
        }
    }
    val shellPadding = LocalShellContentPadding.current
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = shellPadding.calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = HERO_KEY, contentType = HERO_KEY) {
                HeroHeader(
                    state = hero,
                    style = heroStyle,
                    onSubtitleClick = onSubtitleClick,
                    onTitleBottomChange = { titleBottom = it },
                    actions = heroActions,
                )
            }
            content()
        }
        DetailTopBar(
            title = hero.title,
            collapsed = collapsed,
            onBackClick = onBackClick,
            containerColor = hero.tint ?: MaterialTheme.colorScheme.surfaceContainerHigh,
            onPlayClick = onPlayClick,
            onMoreClick = onMoreClick,
        )
    }
}

private const val HERO_KEY = "hero"

@PreviewSegueThemes
@Composable
private fun HeroDetailLayoutPreview() {
    SeguePreview {
        HeroDetailLayout(
            hero = HeroHeaderState(
                overline = "Album",
                title = "In Rainbows",
                subtitle = "Radiohead",
                metadata = "2007 · 10 tracks · 42 min",
                coverArtId = null,
            ),
            onBackClick = {},
            onMoreClick = {},
            heroActions = {
                PlayButton(onClick = {})
                SegueIconButton(icon = SegueIcons.Shuffle, contentDescription = null, onClick = {})
            },
        ) {
            items(PreviewData.tracks.size) { index ->
                TrackRow(state = PreviewData.tracks[index], onClick = {}, leading = TrackRowLeading.Number)
            }
        }
    }
}
