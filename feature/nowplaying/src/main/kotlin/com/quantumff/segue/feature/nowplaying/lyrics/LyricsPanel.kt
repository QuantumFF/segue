package com.quantumff.segue.feature.nowplaying.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.ErrorState
import com.quantumff.segue.core.designsystem.component.InlineSpinner
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.feature.nowplaying.LyricsUiState
import com.quantumff.segue.feature.nowplaying.R
import kotlinx.coroutines.delay

/**
 * Lyrics panel (DESIGN.md §4.7). Synced lyrics hold the active line at 35% of the panel height,
 * at full opacity and scale; other lines are dimmed and slightly smaller. Scrolling by hand pauses
 * the follow until the "Sync" chip is tapped or 5s pass. Tapping a line seeks to it.
 *
 * @param activeLine read here only, so line changes recompose just this panel.
 */
@Composable
internal fun LyricsPanel(
    state: LyricsUiState,
    activeLine: () -> Int,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LyricsUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InlineSpinner() }

        LyricsUiState.Error -> ErrorState(
            title = stringResource(R.string.feature_nowplaying_lyrics_error),
            message = null,
            onRetryClick = onRetry,
            modifier = modifier,
        )

        is LyricsUiState.Loaded -> when (val content = state.content) {
            null -> EmptyState(
                icon = SegueIcons.Lyrics,
                title = stringResource(R.string.feature_nowplaying_lyrics_empty_title),
                message = stringResource(R.string.feature_nowplaying_lyrics_empty_message),
                modifier = modifier,
            )

            is LyricsContent.Synced -> SyncedLyrics(content, activeLine(), onSeek, modifier)

            is LyricsContent.Unsynced -> UnsyncedLyrics(content, modifier)
        }
    }
}

@Composable
private fun SyncedLyrics(
    content: LyricsContent.Synced,
    activeLine: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var following by remember(content) { mutableStateOf(true) }
    // Bumped when a manual scroll ends, restarting the 5s until the follow resumes.
    var manualScrollEnded by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> following = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> manualScrollEnded++
            }
        }
    }
    LaunchedEffect(manualScrollEnded) {
        if (manualScrollEnded == 0) return@LaunchedEffect
        delay(RESUME_FOLLOW_MILLIS)
        following = true
    }
    LaunchedEffect(activeLine, following) {
        if (!following) return@LaunchedEffect
        // The top content padding is 35% of the panel: aligning an item to the start holds it there.
        listState.animateScrollToItem(activeLine.coerceAtLeast(0))
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val anchor = maxHeight * ACTIVE_LINE_POSITION
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = anchor, bottom = maxHeight - anchor),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(content.lines) { index, line ->
                val active = index == activeLine
                val alpha by animateFloatAsState(
                    targetValue = if (active) 1f else INACTIVE_ALPHA,
                    animationSpec = SegueMotion.lyric(),
                    label = "lyricAlpha",
                )
                val scale by animateFloatAsState(
                    targetValue = if (active) 1f else INACTIVE_SCALE,
                    animationSpec = SegueMotion.lyric(),
                    label = "lyricScale",
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = SYNCED_SIZE,
                        fontWeight = FontWeight.Bold,
                        lineHeight = SYNCED_LINE_HEIGHT,
                    ),
                    color = SegueTheme.colors.onArt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clickable {
                            following = true
                            onSeek((content.starts[index] - content.offsetMillis).coerceAtLeast(0))
                        }
                        .padding(horizontal = SegueSpacing.xl, vertical = SegueSpacing.sm),
                )
            }
        }
        AnimatedVisibility(
            visible = !following,
            enter = fadeIn(SegueMotion.medium()),
            exit = fadeOut(SegueMotion.medium()),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(SegueSpacing.lg),
        ) {
            SuggestionChip(
                onClick = { following = true },
                label = { Text(stringResource(R.string.feature_nowplaying_lyrics_sync)) },
                icon = { Icon(SegueIcons.Refresh, contentDescription = null) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    iconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = null,
            )
        }
    }
}

@Composable
private fun UnsyncedLyrics(content: LyricsContent.Unsynced, modifier: Modifier = Modifier) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = SegueSpacing.xl),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(content.lines) { _, line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = UNSYNCED_SIZE,
                    fontWeight = FontWeight.Medium,
                    lineHeight = UNSYNCED_LINE_HEIGHT,
                ),
                color = SegueTheme.colors.onArt,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SegueSpacing.xl, vertical = SegueSpacing.xxs),
            )
        }
    }
}

private const val ACTIVE_LINE_POSITION = 0.35f
private const val INACTIVE_ALPHA = 0.35f
private const val INACTIVE_SCALE = 0.96f
private const val RESUME_FOLLOW_MILLIS = 5_000L
private val SYNCED_SIZE = 24.sp
private val SYNCED_LINE_HEIGHT = 1.25.em
private val UNSYNCED_SIZE = 18.sp
private val UNSYNCED_LINE_HEIGHT = 1.4.em
