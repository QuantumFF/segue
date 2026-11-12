package com.quantumff.segue.feature.nowplaying

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.NowPlayingTopBar
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.LocalReduceMotion
import com.quantumff.segue.core.designsystem.theme.SegueBaseColors
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.ArtistRef
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.RepeatMode
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
import com.quantumff.segue.feature.nowplaying.sheet.ClearQueueDialog
import com.quantumff.segue.feature.nowplaying.sheet.NowPlayingMenu
import com.quantumff.segue.feature.nowplaying.sheet.NowPlayingMenuAction
import com.quantumff.segue.feature.nowplaying.sheet.SaveQueueAsPlaylistDialog
import com.quantumff.segue.feature.nowplaying.sheet.SleepTimerSheet
import com.quantumff.segue.feature.nowplaying.sheet.TrackDetailsSheet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineStart

/**
 * Now Playing (DESIGN.md §4.7).
 *
 * @param queueContent the Queue panel; the app fills it with the queue feature's content.
 * @param start the panel and menu shown when it opens.
 */
@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    queueContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    start: NowPlayingStart = NowPlayingStart.Default,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val position = viewModel.positionMillis.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val activeLyricLine = viewModel.activeLyricLine.collectAsStateWithLifecycle()
    val related by viewModel.related.collectAsStateWithLifecycle()
    val actions = remember(
        viewModel,
        onCollapse,
        onAlbumClick,
        onArtistClick,
        onPlaylistClick,
        onGenreClick,
        onAddToPlaylist,
    ) {
        NowPlayingActions(
            onCollapse = onCollapse,
            onPlayPause = viewModel::togglePlayPause,
            onPrevious = viewModel::skipToPrevious,
            onNext = viewModel::skipToNext,
            onSkipTo = viewModel::skipTo,
            onSeek = viewModel::seekTo,
            onCycleRepeat = viewModel::cycleRepeatMode,
            onToggleFavorite = viewModel::toggleFavorite,
            onStartSleepTimer = viewModel::setSleepTimer,
            onCancelSleepTimer = viewModel::cancelSleepTimer,
            onInstantMix = viewModel::startInstantMix,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onSourceClick = { source ->
                when (source) {
                    is QueueSource.Album -> onAlbumClick(source.id)
                    is QueueSource.Playlist -> onPlaylistClick(source.id)
                    is QueueSource.Artist -> onArtistClick(source.id)
                    is QueueSource.Genre -> onGenreClick(source.name)
                    else -> Unit
                }
            },
            onAddToPlaylist = onAddToPlaylist,
            onVisiblePanelsChange = viewModel::setVisiblePanels,
            onRetryLyrics = { viewModel.retryLyrics() },
            onRetryRelated = { viewModel.retryRelated() },
            onPlayRelated = viewModel::playRelated,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
            onSaveQueueAsPlaylist = viewModel::saveQueueAsPlaylist,
            onClearQueue = viewModel::clearQueue,
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
        modifier = modifier,
        start = start,
    )
}

/** Which sheet or dialog is open. */
private enum class NowPlayingSheet {
    Menu,
    SleepTimer,
    Details,
    SaveQueue,
    ClearQueue,
}

/**
 * Stateless Now Playing, laid out by [nowPlayingLayout]. Portrait phone: art, metadata and controls,
 * with Lyrics / Queue / Related panels replacing the art. Phone landscape: art | controls | the open
 * panel. Expanded width or a taller landscape window: art and controls on the left (45%), the panels
 * as tabs on the right. Tabletop posture: art above the hinge, controls below it, with panels
 * replacing the art. Drag down or back (predictive) collapses; swipe up opens Queue.
 *
 * @param start the panel and menu shown when it opens; later changes are ignored.
 * @param posture how the device is held; read from the window, which Now Playing fills.
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
    start: NowPlayingStart = NowPlayingStart.Default,
    posture: Posture = currentPosture(),
) {
    var openPanel by rememberSaveable { mutableStateOf(start.panel) }
    var sheet by rememberSaveable { mutableStateOf(if (start.openMenu) NowPlayingSheet.Menu else null) }
    val panelDrag = rememberPanelDrag(onClose = { openPanel = null })

    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = nowPlayingLayout(WindowShape(width = maxWidth, height = maxHeight, posture = posture))
        val gestures = rememberDismissGestures(
            onDismiss = actions.onCollapse,
            onSwipeUp = { if (openPanel == null) openPanel = NowPlayingPanel.Queue },
            // A drag down over an open phone panel closes the panel, not the player.
            panelDrag = if (openPanel != null && layout.panelCoversArt) panelDrag else null,
        )
        val panels = remember(actions, lyrics, activeLyricLine, related, queueContent) {
            PanelContent(lyrics, activeLyricLine, related, actions, queueContent)
        }
        val onOpenPanelChange: (NowPlayingPanel?) -> Unit = remember { { openPanel = it } }
        val onMenuClick: () -> Unit = remember { { sheet = NowPlayingSheet.Menu } }
        val track = state.track
        // Rebuilt when the state or the open panel changes, not on position ticks: the position is a lambda.
        val parts = remember(state, track, positionMillis, actions, panels, openPanel) {
            track?.let {
                NowPlayingParts(state, it, positionMillis, actions, panels, openPanel, onOpenPanelChange, onMenuClick)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .then(gestures.modifier),
        ) {
            NowPlayingContent(
                parts = parts,
                coverArtId = track?.coverArtId,
                layout = layout,
                posture = posture,
                panelDrag = panelDrag,
                onCollapse = actions.onCollapse,
            )
        }
        gestures.predictiveBack()
        // Registered after the predictive collapse, so it wins while a panel is open.
        BackHandler(enabled = openPanel != null && !layout.panelAlwaysShown) { openPanel = null }
    }

    val current = state.track
    // Sheets and dialogs sit on ordinary surfaces, in the app's own colors like every other sheet.
    SegueBaseColors {
        when (sheet) {
            NowPlayingSheet.Menu -> if (current != null) {
                NowPlayingMenu(
                    track = current,
                    onAction = { action ->
                        when (action) {
                            NowPlayingMenuAction.SleepTimer -> sheet = NowPlayingSheet.SleepTimer

                            NowPlayingMenuAction.InstantMix -> actions.onInstantMix()

                            NowPlayingMenuAction.AddToPlaylist -> actions.onAddToPlaylist(listOf(current.songId))

                            NowPlayingMenuAction.SaveQueueAsPlaylist -> sheet = NowPlayingSheet.SaveQueue

                            NowPlayingMenuAction.GoToAlbum -> current.albumId?.let(actions.onAlbumClick)

                            NowPlayingMenuAction.GoToArtist ->
                                current.artists.firstOrNull()?.let { actions.onArtistClick(it.id) }

                            NowPlayingMenuAction.Details -> sheet = NowPlayingSheet.Details

                            NowPlayingMenuAction.ClearQueue -> sheet = NowPlayingSheet.ClearQueue
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

            NowPlayingSheet.SaveQueue -> SaveQueueAsPlaylistDialog(
                onConfirm = { name ->
                    sheet = null
                    actions.onSaveQueueAsPlaylist(name)
                },
                onDismiss = { sheet = null },
            )

            NowPlayingSheet.ClearQueue -> ClearQueueDialog(
                trackCount = state.queueSize,
                onConfirm = {
                    sheet = null
                    actions.onClearQueue()
                },
                onDismiss = { sheet = null },
            )

            null -> Unit
        }
    }
}

/** The backdrop and the layout for this window, or [NothingPlaying] without [parts]. The shell draws the snackbar host over it. */
@Composable
private fun NowPlayingContent(
    parts: NowPlayingParts?,
    coverArtId: String?,
    layout: NowPlayingLayout,
    posture: Posture,
    panelDrag: PanelDrag,
    onCollapse: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        NowPlayingBackground(coverArtId = coverArtId, modifier = Modifier.fillMaxSize())
        when {
            parts == null -> NothingPlaying(onCollapse = onCollapse)

            layout == NowPlayingLayout.TwoPane -> TwoPaneLayout(parts)

            layout == NowPlayingLayout.PhoneLandscape -> PhoneLandscapeLayout(parts)

            layout == NowPlayingLayout.Tabletop && posture is Posture.Tabletop -> TabletopLayout(
                parts,
                posture,
                panelDrag,
            )

            else -> PhoneLayout(parts, panelDrag)
        }
        if (parts != null) {
            val shown = layout.shownPanel(parts.openPanel)
            LaunchedEffect(shown) { parts.actions.onVisiblePanelsChange(setOfNotNull(shown)) }
        }
    }
}

/**
 * What every Now Playing layout arranges: the playing [track] with its state and controls, the panels,
 * and which panel is open. It's rebuilt when one of them changes; the position is read through
 * [positionMillis], so a position tick doesn't rebuild it or recompose the layouts.
 */
@Immutable
private class NowPlayingParts(
    val state: NowPlayingUiState,
    val track: NowPlayingTrack,
    val positionMillis: () -> Long,
    val actions: NowPlayingActions,
    val panels: PanelContent,
    val openPanel: NowPlayingPanel?,
    val onOpenPanelChange: (NowPlayingPanel?) -> Unit,
    val onMenuClick: () -> Unit,
)

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

                NowPlayingPanel.Queue -> Box(modifier.fillMaxSize()) { queueContent() }

                NowPlayingPanel.Related -> RelatedPanel(state = related, actions = relatedActions, modifier = modifier)
            }
        }
    }
}

/**
 * Panels sit on the dark art backdrop: their `onSurface` text and icons turn light in every theme, and
 * their containers (the lyrics Sync chip, the Continue-from-device banner, a dragged queue row) are the
 * theme's art containers, dark and opaque, so that light content stays readable and nothing under a
 * floating chip or row shows through.
 */
@Composable
private fun OnArtContent(content: @Composable () -> Unit) {
    val colors = SegueTheme.colors
    val containers = colors.artContainers
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            onSurface = colors.onArt,
            onSurfaceVariant = colors.onArtMuted,
            onBackground = colors.onArt,
            surfaceContainerLowest = containers.surfaceContainerLowest,
            surfaceContainerLow = containers.surfaceContainerLow,
            surfaceContainer = containers.surfaceContainer,
            surfaceContainerHigh = containers.surfaceContainerHigh,
            surfaceContainerHighest = containers.surfaceContainerHighest,
            // Tonal buttons (Play all, Retry): a light veil instead of the accent tint.
            secondaryContainer = colors.onArt.copy(alpha = TONAL_ON_ART_ALPHA),
            onSecondaryContainer = colors.onArt,
            // The accent doesn't hold contrast on every backdrop, so on-art highlights are light too: the
            // current queue row is a light veil with light text, marked by its equalizer bars.
            primary = colors.onArt,
            primaryContainer = colors.onArt.copy(alpha = TONAL_ON_ART_ALPHA),
            onPrimaryContainer = colors.onArt,
            outline = colors.onArt.copy(alpha = OUTLINE_ON_ART_ALPHA),
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

@Composable
private fun PhoneLayout(parts: NowPlayingParts, panelDrag: PanelDrag) {
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        parts.TopBar()
        parts.ArtOrPanel(
            panelDrag = panelDrag,
            panelHeader = { CompactTrackHeader(track = parts.track, onClick = { parts.onOpenPanelChange(null) }) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // Art and metadata stay together, centered in the space above the controls.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val artHeight = min(maxWidth - SegueSpacing.xl, maxHeight - METADATA_ALLOWANCE)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    parts.Art(
                        Modifier
                            .fillMaxWidth()
                            .height(artHeight),
                    )
                    parts.Metadata()
                }
            }
        }
        parts.Controls()
        parts.BottomRow()
    }
}

/**
 * The art, drawn by [art], or the open panel in its place under an optional [panelHeader]: a panel
 * opening or closing slides (or, with reduce motion, crossfades) over the art.
 */
@Composable
private fun NowPlayingParts.ArtOrPanel(
    panelDrag: PanelDrag,
    modifier: Modifier = Modifier,
    panelHeader: @Composable () -> Unit = {},
    art: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = openPanel != null,
        transitionSpec = { NowPlayingMotion.panelTransition(reduceMotion, draggedDown = panelDrag.isClosing) },
        label = "panel",
        modifier = modifier.clipToBounds(),
    ) { showingPanel ->
        if (showingPanel) PanelInPlaceOfArt(this@ArtOrPanel, panelDrag, panelHeader) else art()
    }
}

/**
 * The open panel where the art was, under an optional [header]: it pages between panels and follows a
 * drag down, fading, until [panelDrag] closes it.
 */
@Composable
private fun PanelInPlaceOfArt(parts: NowPlayingParts, panelDrag: PanelDrag, header: @Composable () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .onSizeChanged { panelDrag.height = it.height.toFloat() }
            .graphicsLayer {
                val pulled = panelDrag.value
                translationY = pulled
                if (panelDrag.height > 0f) {
                    alpha = 1f - PANEL_DRAG_FADE * (pulled / panelDrag.height).coerceIn(0f, 1f)
                }
            }
            .nestedScroll(panelDrag.nestedScroll),
    ) {
        DisposableEffect(panelDrag) { onDispose(panelDrag::onPanelGone) }
        header()
        val pagerState = rememberPagerState(initialPage = (parts.openPanel ?: NowPlayingPanel.Lyrics).ordinal) {
            NowPlayingPanel.entries.size
        }
        SyncPager(pagerState, NowPlayingPanel.entries, parts.openPanel) { parts.onOpenPanelChange(it) }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), key = { it }) { page ->
            parts.panels.Panel(NowPlayingPanel.entries[page], Modifier.fillMaxSize())
        }
    }
}

/**
 * A foldable in tabletop posture: the top bar and the art, or the open panel in its place, above the
 * hinge; the metadata, seekbar, transport and bottom row below it, where the device rests.
 */
@Composable
private fun TabletopLayout(parts: NowPlayingParts, hinge: Posture.Tabletop, panelDrag: PanelDrag) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(hinge.hingeTop),
        ) {
            parts.TopBar()
            parts.ArtOrPanel(
                panelDrag = panelDrag,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                parts.Art(Modifier.fillMaxSize())
            }
        }
        // Nothing is drawn across the hinge itself.
        Spacer(Modifier.height(hinge.hingeBottom - hinge.hingeTop))
        parts.MetadataControlsAndBottomRow(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

/** The posture of the window Now Playing fills, from its folding feature. */
@Composable
private fun currentPosture(): Posture {
    val hinges = currentWindowAdaptiveInfo().windowPosture.hingeList
    val density = LocalDensity.current
    return remember(hinges, density) { postureOf(hinges, density) }
}

/**
 * A phone held sideways: the art, sized from the height, then the metadata, seekbar, transport and
 * bottom row, then the open panel. The panel's area appears only while a panel is open; the widths
 * come from [phoneLandscapeWidths], which keeps the art large.
 */
@Composable
private fun PhoneLandscapeLayout(parts: NowPlayingParts) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        val closed = phoneLandscapeWidths(maxWidth, maxHeight, statusBar, panelOpen = false)
        val open = phoneLandscapeWidths(maxWidth, maxHeight, statusBar, panelOpen = true)
        val panelWidth = if (parts.openPanel == null) 0.dp else open.panel
        val grows = NowPlayingMotion.sidePanelGrows(LocalReduceMotion.current)
        val panelSpec: AnimationSpec<Dp> = if (grows) SegueMotion.longEnter() else snap()
        val animatedPanelWidth by animateDpAsState(panelWidth, panelSpec, label = "panelWidth")
        // The art gives up to the growing panel only the width the open layout takes from it.
        val artSide = min(closed.art, open.art + open.panel - animatedPanelWidth).coerceAtLeast(open.art)
        Row(Modifier.fillMaxSize()) {
            parts.Art(
                Modifier
                    .padding(top = statusBar)
                    .width(artSide + LANDSCAPE_ART_PADDING * 2)
                    .fillMaxHeight(),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                parts.TopBar()
                parts.MetadataControlsAndBottomRow(Modifier.weight(1f))
            }
            // The panel keeps its full width while its area grows or shrinks, so its pager never
            // lays out at a sliver of a page.
            var laidOutWidth by remember { mutableStateOf(panelWidth) }
            if (panelWidth > 0.dp) laidOutWidth = panelWidth
            Box(
                Modifier
                    .width(animatedPanelWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                SidePanel(
                    parts = parts,
                    modifier = Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(laidOutWidth)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/** The phone landscape panel area: the open panel, which crossfades in and out. */
@Composable
private fun SidePanel(parts: NowPlayingParts, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = parts.openPanel != null,
        enter = fadeIn(SegueMotion.medium()),
        exit = fadeOut(SegueMotion.medium()),
        modifier = modifier,
    ) {
        // Keeps the closing panel's page while it fades.
        var shown by remember { mutableStateOf(parts.openPanel ?: NowPlayingPanel.Lyrics) }
        if (parts.openPanel != null) shown = parts.openPanel
        val pagerState = rememberPagerState(initialPage = shown.ordinal) { NowPlayingPanel.entries.size }
        SyncPager(pagerState, NowPlayingPanel.entries, parts.openPanel) { parts.onOpenPanelChange(it) }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            key = { it },
        ) { page ->
            parts.panels.Panel(NowPlayingPanel.entries[page], Modifier.fillMaxSize())
        }
    }
}

/**
 * Art and controls on the left, the panels as tabs on the right. The tab shown is the open panel, the
 * same one the other layouts show, or Queue when none is open; choosing a tab opens that panel.
 */
@Composable
private fun TwoPaneLayout(parts: NowPlayingParts) {
    val tabs = TWO_PANE_TABS
    val shown = NowPlayingLayout.TwoPane.shownPanel(parts.openPanel) ?: tabs.first()
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(shown)) { tabs.size }
    // A swipe to another tab opens it; settling on Queue with no panel open leaves none open.
    SyncPager(pagerState, tabs, parts.openPanel) { settled -> if (settled != shown) parts.onOpenPanelChange(settled) }
    Row(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.weight(ART_PANE_WEIGHT)) {
            parts.TopBar()
            // Art, metadata and controls stay together, centered, instead of the art floating in a
            // tall pane far from its title and controls.
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val artHeight = min(maxWidth - SegueSpacing.xl, maxHeight - METADATA_ALLOWANCE - CONTROLS_ALLOWANCE)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    parts.Art(
                        Modifier
                            .fillMaxWidth()
                            .height(artHeight),
                    )
                    parts.Metadata()
                    parts.Controls()
                }
            }
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
                        onClick = { parts.onOpenPanelChange(panel) },
                        selectedContentColor = SegueTheme.colors.onArt,
                        unselectedContentColor = SegueTheme.colors.onArtMuted,
                        text = { Text(stringResource(panel.label), style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), key = { it }) { page ->
                parts.panels.Panel(tabs[page], Modifier.fillMaxSize())
            }
        }
    }
}

/** The art pager: swipe to skip, tap to open Lyrics, long-press for the menu. */
@Composable
private fun NowPlayingParts.Art(modifier: Modifier = Modifier) {
    ArtPager(
        track = track,
        previous = state.previous,
        next = state.next,
        onSkipTo = actions.onSkipTo,
        onClick = { onOpenPanelChange(NowPlayingPanel.Lyrics) },
        onLongClick = onMenuClick,
        modifier = modifier,
    )
}

/** The title, artists, album and heart. */
@Composable
private fun NowPlayingParts.Metadata() {
    TrackMetadata(
        track = track,
        onArtistClick = actions.onArtistClick,
        onAlbumClick = actions.onAlbumClick,
        onFavoriteClick = actions.onToggleFavorite,
        modifier = Modifier.padding(start = SegueSpacing.xl, end = SegueSpacing.sm),
    )
}

/**
 * The metadata, seekbar and transport, centered in the space above the bottom row, for the layouts
 * that keep them apart from the art.
 */
@Composable
private fun NowPlayingParts.MetadataControlsAndBottomRow(modifier: Modifier = Modifier) {
    Column(modifier) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Metadata()
            Controls()
        }
        BottomRow()
    }
}

/** Seekbar and transport, pinned under the art or panels. */
@Composable
private fun NowPlayingParts.Controls() {
    Column(Modifier.fillMaxWidth()) {
        NowPlayingSeekbar(
            positionMillis = positionMillis,
            durationMillis = state.durationMillis,
            // The bar glides only while the position moves, not while buffering.
            isPlaying = state.isPlaying && !state.isBuffering,
            quality = state.quality,
            onSeek = actions.onSeek,
            modifier = Modifier.padding(horizontal = SegueSpacing.xl, vertical = SegueSpacing.xs),
        )
        TransportRow(
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            repeatMode = state.repeatMode,
            onPreviousClick = actions.onPrevious,
            onPlayPauseClick = actions.onPlayPause,
            onNextClick = actions.onNext,
            onRepeatClick = actions.onCycleRepeat,
            modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm),
        )
    }
}

/** Lyrics · Queue · Related · ⋮: tapping the open panel's button closes it. */
@Composable
private fun NowPlayingParts.BottomRow() {
    PanelBar(
        openPanel = openPanel,
        sleepTimerActive = state.sleepTimer != null,
        onPanelClick = { panel -> onOpenPanelChange(if (panel == openPanel) null else panel) },
        onMoreClick = onMenuClick,
        modifier = Modifier.padding(bottom = SegueSpacing.sm),
    )
}

@Composable
private fun NowPlayingParts.TopBar() {
    val source = state.source
    NowPlayingTopBar(
        sourceLabel = stringResource(source.overline),
        sourceName = source.name,
        onCollapseClick = actions.onCollapse,
        onMoreClick = onMenuClick,
        onSourceClick = source.opens?.let { opens -> { actions.onSourceClick(opens) } },
        sourceClickLabel = source.name?.let { stringResource(R.string.feature_nowplaying_open_source, it) },
    )
}

@Composable
private fun NothingPlaying(onCollapse: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        NowPlayingTopBar(
            sourceLabel = stringResource(R.string.feature_nowplaying_source_queue),
            sourceName = stringResource(R.string.feature_nowplaying_nothing_playing),
            onCollapseClick = onCollapse,
            onMoreClick = null,
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

/** Keeps a panel pager and the open panel in step, both ways. */
@Composable
private fun SyncPager(
    pagerState: PagerState,
    order: List<NowPlayingPanel>,
    openPanel: NowPlayingPanel?,
    onSettle: (NowPlayingPanel) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(openPanel) {
        val page = openPanel?.let(order::indexOf) ?: return@LaunchedEffect
        if (pagerState.currentPage != page) pagerState.showPanelPage(page, reduceMotion)
    }
    val latestOnSettle by rememberUpdatedState(onSettle)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { latestOnSettle(order[it]) }
    }
}

private val TWO_PANE_TABS = persistentListOf(NowPlayingPanel.Queue, NowPlayingPanel.Lyrics, NowPlayingPanel.Related)

private val INDICATOR_HEIGHT = 2.dp

/** Room under the art for the title, artists and heart. */
private val METADATA_ALLOWANCE = 72.dp

/** Room for the seekbar and transport row under the art in the two-pane layout. */
private val CONTROLS_ALLOWANCE = 180.dp
private const val ART_PANE_WEIGHT = 0.45f

/** How much a panel fades when pulled down its whole height. */
private const val PANEL_DRAG_FADE = 0.5f
private const val TONAL_ON_ART_ALPHA = 0.16f
private const val OUTLINE_ON_ART_ALPHA = 0.32f

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
    repeatMode = RepeatMode.All,
    quality = "FLAC · 16/44.1",
)

private const val PREVIEW_POSITION = 112_000L
