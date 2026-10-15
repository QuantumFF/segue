package com.quantumff.segue.feature.nowplaying

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.NowPlayingTopBar
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.ArtistRef
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.feature.nowplaying.component.ArtPager
import com.quantumff.segue.feature.nowplaying.component.CompactTrackHeader
import com.quantumff.segue.feature.nowplaying.component.NowPlayingBackground
import com.quantumff.segue.feature.nowplaying.component.NowPlayingSeekbar
import com.quantumff.segue.feature.nowplaying.component.PanelBar
import com.quantumff.segue.feature.nowplaying.component.TrackMetadata
import com.quantumff.segue.feature.nowplaying.component.TransportRow
import com.quantumff.segue.feature.nowplaying.component.label
import com.quantumff.segue.feature.nowplaying.lyrics.LyricsContent
import com.quantumff.segue.feature.nowplaying.lyrics.LyricsPanel
import com.quantumff.segue.feature.nowplaying.related.RelatedActions
import com.quantumff.segue.feature.nowplaying.related.RelatedPanel
import com.quantumff.segue.feature.nowplaying.sheet.NowPlayingMenu
import com.quantumff.segue.feature.nowplaying.sheet.NowPlayingMenuAction
import com.quantumff.segue.feature.nowplaying.sheet.SleepTimerSheet
import com.quantumff.segue.feature.nowplaying.sheet.TrackDetailsSheet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

/**
 * Now Playing (DESIGN.md §4.7).
 *
 * @param queueContent the Up next panel; the app fills it with the queue screen's list.
 */
@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    queueContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val position = viewModel.positionMillis.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val activeLyricLine = viewModel.activeLyricLine.collectAsStateWithLifecycle()
    val related by viewModel.related.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val instantMixFailed = stringResource(R.string.feature_nowplaying_instant_mix_failed)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                NowPlayingMessage.InstantMixFailed -> snackbarHostState.showSnackbar(instantMixFailed)
            }
        }
    }
    val actions = remember(viewModel, onCollapse, onAlbumClick, onArtistClick, onAddToPlaylist) {
        NowPlayingActions(
            onCollapse = onCollapse,
            onPlayPause = viewModel::togglePlayPause,
            onPrevious = viewModel::skipToPrevious,
            onNext = viewModel::skipToNext,
            onSkipTo = viewModel::skipTo,
            onSeek = viewModel::seekTo,
            onCycleShuffle = viewModel::cycleShuffleMode,
            onCycleRepeat = viewModel::cycleRepeatMode,
            onToggleFavorite = viewModel::toggleFavorite,
            onStartSleepTimer = viewModel::setSleepTimer,
            onCancelSleepTimer = viewModel::cancelSleepTimer,
            onInstantMix = viewModel::startInstantMix,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onAddToPlaylist = onAddToPlaylist,
            onVisiblePanelsChange = viewModel::setVisiblePanels,
            onRetryLyrics = { viewModel.retryLyrics() },
            onRetryRelated = { viewModel.retryRelated() },
            onPlayRelated = viewModel::playRelated,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
        )
    }
    NowPlayingScreen(
        state = state,
        positionMillis = { position.value },
        lyrics = lyrics,
        activeLyricLine = { activeLyricLine.value },
        related = related,
        actions = actions,
        queueContent = queueContent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Which sheet is open. */
private enum class NowPlayingSheet {
    Menu,
    SleepTimer,
    Details,
}

/**
 * Stateless Now Playing. Phone: art, metadata and controls, with Lyrics / Up next / Related
 * panels replacing the art. Expanded width or landscape: art and controls on the left (45%), the
 * panels as tabs on the right. Drag down or back (predictive) collapses; swipe up opens Up next.
 */
@Composable
internal fun NowPlayingScreen(
    state: NowPlayingUiState,
    positionMillis: () -> Long,
    lyrics: LyricsUiState,
    activeLyricLine: () -> Int,
    related: RelatedUiState,
    actions: NowPlayingActions,
    queueContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var openPanel by rememberSaveable { mutableStateOf<NowPlayingPanel?>(null) }
    var sheet by rememberSaveable { mutableStateOf<NowPlayingSheet?>(null) }
    val gestures = rememberDismissGestures(
        onDismiss = actions.onCollapse,
        onSwipeUp = { if (openPanel == null) openPanel = NowPlayingPanel.UpNext },
    )

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .then(gestures.modifier),
    ) {
        val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH || (maxWidth > maxHeight && maxWidth >= LANDSCAPE_TWO_PANE_MIN_WIDTH)
        NowPlayingBackground(coverArtId = state.track?.coverArtId, modifier = Modifier.fillMaxSize())
        val track = state.track
        val panels = remember(actions, lyrics, activeLyricLine, related, queueContent) {
            PanelContent(lyrics, activeLyricLine, related, actions, queueContent)
        }
        when {
            track == null -> NothingPlaying(onCollapse = actions.onCollapse)

            twoPane -> TwoPaneLayout(
                state = state,
                track = track,
                positionMillis = positionMillis,
                actions = actions,
                panels = panels,
                onMenuClick = { sheet = NowPlayingSheet.Menu },
            )

            else -> PhoneLayout(
                state = state,
                track = track,
                positionMillis = positionMillis,
                actions = actions,
                panels = panels,
                openPanel = openPanel,
                onOpenPanelChange = { openPanel = it },
                onMenuClick = { sheet = NowPlayingSheet.Menu },
            )
        }
        SegueSnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
        if (track != null && !twoPane) {
            LaunchedEffect(openPanel) { actions.onVisiblePanelsChange(setOfNotNull(openPanel)) }
        }
        gestures.predictiveBack()
        // Registered after the predictive collapse, so it wins while a panel is open.
        BackHandler(enabled = openPanel != null && !twoPane) { openPanel = null }
    }

    val current = state.track
    when (sheet) {
        NowPlayingSheet.Menu -> if (current != null) {
            NowPlayingMenu(
                track = current,
                onAction = { action ->
                    when (action) {
                        NowPlayingMenuAction.SleepTimer -> sheet = NowPlayingSheet.SleepTimer
                        NowPlayingMenuAction.InstantMix -> actions.onInstantMix()
                        NowPlayingMenuAction.AddToPlaylist -> actions.onAddToPlaylist(listOf(current.songId))
                        NowPlayingMenuAction.GoToAlbum -> current.albumId?.let(actions.onAlbumClick)
                        NowPlayingMenuAction.GoToArtist -> current.artists.firstOrNull()?.let { actions.onArtistClick(it.id) }
                        NowPlayingMenuAction.Details -> sheet = NowPlayingSheet.Details
                    }
                },
                onDismissRequest = { if (sheet == NowPlayingSheet.Menu) sheet = null },
            )
        }

        NowPlayingSheet.SleepTimer -> SleepTimerSheet(
            active = state.sleepTimer,
            onStart = actions.onStartSleepTimer,
            onCancel = actions.onCancelSleepTimer,
            onDismissRequest = { sheet = null },
        )

        NowPlayingSheet.Details -> TrackDetailsSheet(details = state.details, onDismissRequest = { sheet = null })

        null -> Unit
    }
}

/** The three panels' bodies, drawn with light content over the art backdrop. */
private class PanelContent(
    val lyrics: LyricsUiState,
    val activeLyricLine: () -> Int,
    val related: RelatedUiState,
    val actions: NowPlayingActions,
    val queueContent: @Composable () -> Unit,
) {
    private val relatedActions = RelatedActions(
        onPlay = actions.onPlayRelated,
        onPlayNext = actions.onPlayNext,
        onAddToQueue = actions.onAddToQueue,
        onAddToPlaylist = { actions.onAddToPlaylist(listOf(it.id)) },
        onAlbumClick = actions.onAlbumClick,
        onArtistClick = actions.onArtistClick,
        onRetry = actions.onRetryRelated,
    )

    @Composable
    fun Panel(panel: NowPlayingPanel, modifier: Modifier = Modifier) {
        OnArtContent {
            when (panel) {
                NowPlayingPanel.Lyrics -> LyricsPanel(
                    state = lyrics,
                    activeLine = activeLyricLine,
                    onSeek = actions.onSeek,
                    onRetry = actions.onRetryLyrics,
                    modifier = modifier,
                )

                NowPlayingPanel.UpNext -> Box(modifier.fillMaxSize()) { queueContent() }

                NowPlayingPanel.Related -> RelatedPanel(state = related, actions = relatedActions, modifier = modifier)
            }
        }
    }
}

/** Panels sit on the dark art backdrop: their `onSurface` text and icons turn light in every theme. */
@Composable
private fun OnArtContent(content: @Composable () -> Unit) {
    val colors = SegueTheme.colors
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            onSurface = colors.onArt,
            onSurfaceVariant = colors.onArtMuted,
            onBackground = colors.onArt,
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

@Composable
private fun PhoneLayout(
    state: NowPlayingUiState,
    track: NowPlayingTrack,
    positionMillis: () -> Long,
    actions: NowPlayingActions,
    panels: PanelContent,
    openPanel: NowPlayingPanel?,
    onOpenPanelChange: (NowPlayingPanel?) -> Unit,
    onMenuClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        TopBar(track, actions.onCollapse, onMenuClick)
        AnimatedContent(
            targetState = openPanel != null,
            transitionSpec = {
                (slideInVertically(SegueMotion.longEnter()) { it / PANEL_SLIDE_FRACTION } + fadeIn(SegueMotion.longEnter()))
                    .togetherWith(fadeOut(SegueMotion.medium()))
            },
            label = "panel",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { showingPanel ->
            if (showingPanel) {
                Column(Modifier.fillMaxSize()) {
                    CompactTrackHeader(track = track, onClick = { onOpenPanelChange(null) })
                    val pagerState = rememberPagerState(initialPage = (openPanel ?: NowPlayingPanel.Lyrics).ordinal) {
                        NowPlayingPanel.entries.size
                    }
                    SyncPager(pagerState, NowPlayingPanel.entries, openPanel) { onOpenPanelChange(it) }
                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), key = { it }) { page ->
                        panels.Panel(NowPlayingPanel.entries[page], Modifier.fillMaxSize())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ArtPager(
                        track = track,
                        previous = state.previous,
                        next = state.next,
                        onSkipTo = actions.onSkipTo,
                        onClick = { onOpenPanelChange(NowPlayingPanel.Lyrics) },
                        onLongClick = onMenuClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    TrackMetadata(
                        track = track,
                        onArtistClick = actions.onArtistClick,
                        onAlbumClick = actions.onAlbumClick,
                        onFavoriteClick = actions.onToggleFavorite,
                        modifier = Modifier.padding(start = SegueSpacing.xl, end = SegueSpacing.sm),
                    )
                }
            }
        }
        Controls(state, positionMillis, actions)
        PanelBar(
            openPanel = openPanel,
            sleepTimerActive = state.sleepTimer != null,
            onPanelClick = { panel -> onOpenPanelChange(if (panel == openPanel) null else panel) },
            onMoreClick = onMenuClick,
            modifier = Modifier.padding(bottom = SegueSpacing.sm),
        )
    }
}

@Composable
private fun TwoPaneLayout(
    state: NowPlayingUiState,
    track: NowPlayingTrack,
    positionMillis: () -> Long,
    actions: NowPlayingActions,
    panels: PanelContent,
    onMenuClick: () -> Unit,
) {
    val tabs = TWO_PANE_TABS
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> actions.onVisiblePanelsChange(setOf(tabs[page])) }
    }
    Row(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.weight(ART_PANE_WEIGHT)) {
            TopBar(track, actions.onCollapse, onMenuClick)
            ArtPager(
                track = track,
                previous = state.previous,
                next = state.next,
                onSkipTo = actions.onSkipTo,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(tabs.indexOf(NowPlayingPanel.Lyrics)) }
                },
                onLongClick = onMenuClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            TrackMetadata(
                track = track,
                onArtistClick = actions.onArtistClick,
                onAlbumClick = actions.onAlbumClick,
                onFavoriteClick = actions.onToggleFavorite,
                modifier = Modifier.padding(start = SegueSpacing.xl, end = SegueSpacing.sm),
            )
            Controls(state, positionMillis, actions)
            Spacer(Modifier.height(SegueSpacing.lg))
        }
        Column(
            Modifier
                .weight(1f - ART_PANE_WEIGHT)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = SegueTheme.colors.onArt,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(pagerState.currentPage, matchContentSize = false),
                        width = Dp.Unspecified,
                        height = INDICATOR_HEIGHT,
                        color = MaterialTheme.colorScheme.primary,
                        shape = SegueShapes.xs,
                    )
                },
                divider = {},
            ) {
                tabs.forEachIndexed { index, panel ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        selectedContentColor = SegueTheme.colors.onArt,
                        unselectedContentColor = SegueTheme.colors.onArtMuted,
                        text = { Text(stringResource(panel.label), style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), key = { it }) { page ->
                panels.Panel(tabs[page], Modifier.fillMaxSize())
            }
        }
    }
}

/** Seekbar and transport, pinned under the art or panels. */
@Composable
private fun Controls(state: NowPlayingUiState, positionMillis: () -> Long, actions: NowPlayingActions) {
    NowPlayingSeekbar(
        positionMillis = positionMillis,
        durationMillis = state.durationMillis,
        isPlaying = state.isPlaying,
        quality = state.quality,
        onSeek = actions.onSeek,
        modifier = Modifier.padding(horizontal = SegueSpacing.xl, vertical = SegueSpacing.xs),
    )
    TransportRow(
        isPlaying = state.isPlaying,
        shuffleMode = state.shuffleMode,
        repeatMode = state.repeatMode,
        onShuffleClick = actions.onCycleShuffle,
        onPreviousClick = actions.onPrevious,
        onPlayPauseClick = actions.onPlayPause,
        onNextClick = actions.onNext,
        onRepeatClick = actions.onCycleRepeat,
        modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm),
    )
}

@Composable
private fun TopBar(track: NowPlayingTrack, onCollapse: () -> Unit, onMenuClick: () -> Unit) {
    NowPlayingTopBar(
        sourceLabel = stringResource(R.string.feature_nowplaying_source_label),
        sourceName = track.album ?: track.title,
        onCollapseClick = onCollapse,
        onMoreClick = onMenuClick,
    )
}

@Composable
private fun NothingPlaying(onCollapse: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        NowPlayingTopBar(
            sourceLabel = stringResource(R.string.feature_nowplaying_source_queue),
            sourceName = stringResource(R.string.feature_nowplaying_nothing_playing),
            onCollapseClick = onCollapse,
            onMoreClick = {},
        )
        OnArtContent {
            EmptyState(
                icon = SegueIcons.Music,
                title = stringResource(R.string.feature_nowplaying_nothing_playing),
                message = null,
            )
        }
    }
}

/** Keeps the phone panel pager and the open panel in step, both ways. */
@Composable
private fun SyncPager(
    pagerState: PagerState,
    order: List<NowPlayingPanel>,
    openPanel: NowPlayingPanel?,
    onSettled: (NowPlayingPanel) -> Unit,
) {
    LaunchedEffect(openPanel) {
        val page = openPanel?.let(order::indexOf) ?: return@LaunchedEffect
        if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onSettled(order[it]) }
    }
}

/** Drag-down and predictive-back collapse, and drag-up to open Up next. */
private class DismissGestures(
    val modifier: Modifier,
    val predictiveBack: @Composable () -> Unit,
)

@Composable
private fun rememberDismissGestures(onDismiss: () -> Unit, onSwipeUp: () -> Unit): DismissGestures {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragOffset = remember { Animatable(0f) }
    val backProgress = remember { Animatable(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var rawDrag by remember { mutableFloatStateOf(0f) }
    val flingPx = with(density) { DISMISS_FLING_DP_PER_SECOND.dp.toPx() }
    val backShiftPx = with(density) { BACK_SHIFT.toPx() }
    val dragState = rememberDraggableState { delta ->
        rawDrag += delta
        scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
    }
    val modifier = Modifier
        .onSizeChanged { height = it.height.toFloat() }
        .graphicsLayer {
            val back = backProgress.value
            translationY = dragOffset.value + back * backShiftPx
            scaleX = 1f - back * BACK_SCALE
            scaleY = 1f - back * BACK_SCALE
            if (back > 0f) {
                shape = SegueShapes.xl
                clip = true
            }
        }
        .draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStarted = { rawDrag = 0f },
            onDragStopped = { velocity ->
                val offset = dragOffset.value
                when {
                    offset > height * DISMISS_FRACTION || (offset > 0f && velocity > flingPx) -> onDismiss()

                    rawDrag < -height * SWIPE_UP_FRACTION || velocity < -flingPx -> {
                        onSwipeUp()
                        dragOffset.animateTo(0f, SegueMotion.player())
                    }

                    else -> dragOffset.animateTo(0f, SegueMotion.player(), velocity)
                }
            },
        )
    val predictiveBack: @Composable () -> Unit = {
        PredictiveBackHandler { progress ->
            try {
                progress.collect { backProgress.snapTo(it.progress) }
                onDismiss()
            } catch (e: CancellationException) {
                scope.launch { backProgress.animateTo(0f, SegueMotion.player()) }
                throw e
            }
        }
    }
    return DismissGestures(modifier, predictiveBack)
}

private val TWO_PANE_TABS = persistentListOf(NowPlayingPanel.UpNext, NowPlayingPanel.Lyrics, NowPlayingPanel.Related)
private val TWO_PANE_MIN_WIDTH = 840.dp
private val LANDSCAPE_TWO_PANE_MIN_WIDTH = 600.dp
private val INDICATOR_HEIGHT = 2.dp
private val BACK_SHIFT = 48.dp
private const val ART_PANE_WEIGHT = 0.45f
private const val PANEL_SLIDE_FRACTION = 4
private const val DISMISS_FRACTION = 0.25f
private const val SWIPE_UP_FRACTION = 0.15f
private const val DISMISS_FLING_DP_PER_SECOND = 1000
private const val BACK_SCALE = 0.1f

@PreviewSegueThemes
@Composable
private fun NowPlayingScreenPreview() {
    SeguePreview {
        NowPlayingScreen(
            state = PreviewState,
            positionMillis = { PREVIEW_POSITION },
            lyrics = LyricsUiState.Loaded(LyricsContent.Unsynced(persistentListOf("Don't get any big ideas"))),
            activeLyricLine = { -1 },
            related = RelatedUiState.Loading,
            actions = NowPlayingActions.None,
            queueContent = {},
        )
    }
}

internal val PreviewState = NowPlayingUiState(
    track = NowPlayingTrack(
        songId = "s-3",
        timelineIndex = 2,
        title = "Nude",
        artist = "Radiohead",
        artists = persistentListOf(ArtistRef("ar-1", "Radiohead")),
        album = "In Rainbows",
        albumId = "al-1",
        coverArtId = null,
        isStarred = true,
    ),
    isPlaying = true,
    durationMillis = 255_000,
    shuffleMode = ShuffleMode.Albums,
    repeatMode = RepeatMode.All,
    quality = "FLAC · 16/44.1",
)

private const val PREVIEW_POSITION = 112_000L
