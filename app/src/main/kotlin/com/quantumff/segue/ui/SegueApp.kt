package com.quantumff.segue.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.window.core.layout.WindowSizeClass
import com.quantumff.segue.R
import com.quantumff.segue.core.designsystem.component.ContinueFromDeviceBar
import com.quantumff.segue.core.designsystem.component.ContinueFromDeviceState
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MiniPlayer
import com.quantumff.segue.core.designsystem.component.PlayerBar
import com.quantumff.segue.core.designsystem.component.PlayerBarActions
import com.quantumff.segue.core.designsystem.component.PlayerBarState
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.feature.login.navigation.LoginRoute
import com.quantumff.segue.feature.nowplaying.navigation.NowPlayingRoute
import com.quantumff.segue.feature.nowplaying.navigation.navigateToNowPlaying
import com.quantumff.segue.feature.playlist.AddToPlaylistSheet
import com.quantumff.segue.feature.playlist.navigation.navigateToPlaylist
import com.quantumff.segue.feature.queue.navigation.navigateToQueue
import com.quantumff.segue.navigation.HomeGraph
import com.quantumff.segue.navigation.SegueNavHost
import com.quantumff.segue.navigation.TopLevelDestination
import com.quantumff.segue.playback.PlayerMessage
import com.quantumff.segue.playback.PlayerViewModel
import com.quantumff.segue.playback.ShellPlayerActions
import com.quantumff.segue.playback.ShellPlayerState
import com.quantumff.segue.playback.progressOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * App shell entry point: picks the layout for the window size (DESIGN.md §3), starts at login
 * when no server is configured, and hosts the add-to-playlist sheet that any screen can raise.
 */
@Composable
fun SegueApp(
    hasServer: Boolean,
    modifier: Modifier = Modifier,
    openNowPlayingRequest: Int = 0,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    sidebarViewModel: SidebarViewModel = hiltViewModel(),
) {
    val appState = rememberSegueAppState(shellLayout = windowAdaptiveInfo.windowSizeClass.toShellLayout())
    // Only the expanded sidebar lists playlists: other layouts do not observe them.
    val sidebarPlaylists = if (appState.shellLayout == ShellLayout.Expanded) {
        sidebarViewModel.playlists.collectAsStateWithLifecycle().value
    } else {
        persistentListOf()
    }
    val onSidebarPlaylistClick = remember(appState) { { id: String -> appState.navigate { navigateToPlaylist(id) } } }
    val player by playerViewModel.player.collectAsStateWithLifecycle()
    val playActions = playerViewModel.playActions
    val playerActions = remember(playerViewModel) { playerViewModel.shellActions() }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(playerViewModel) {
        playerViewModel.messages.collect { message ->
            when (message) {
                PlayerMessage.NothingToPlay ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.player_nothing_to_play))

                PlayerMessage.InstantMixFailed ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.player_instant_mix_failed))

                // DESIGN.md §4.8: with auto-continue on, a snackbar replaces the banner, with Undo.
                is PlayerMessage.ContinuedFrom -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.player_continued_from, message.deviceName),
                        actionLabel = if (message.canUndo) resources.getString(R.string.player_undo) else null,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) playerViewModel.undoAutoAdoption()
                }
            }
        }
    }
    var addToPlaylist by remember { mutableStateOf<List<String>?>(null) }
    // Fixed for the NavHost's lifetime; later sign-outs navigate to login instead.
    val startDestination: Any = remember { if (hasServer) HomeGraph else LoginRoute() }
    val destination = appState.currentDestination
    LaunchedEffect(hasServer, destination) {
        if (!hasServer && destination != null && !destination.hasRoute(LoginRoute::class)) appState.navigateToLogin()
    }
    // A widget tap: open Now Playing once per request, unless it is already showing.
    LaunchedEffect(openNowPlayingRequest) {
        val current = appState.navController.currentDestination
        if (openNowPlayingRequest > 0 && hasServer && current?.hasRoute(NowPlayingRoute::class) != true) {
            appState.navigate { navigateToNowPlaying() }
        }
    }

    SegueApp(
        appState = appState,
        player = player,
        positionMillis = playerViewModel.positionMillis,
        playerActions = playerActions,
        modifier = modifier,
        sidebarPlaylists = sidebarPlaylists,
        onSidebarPlaylistClick = onSidebarPlaylistClick,
    ) { navModifier ->
        Box(navModifier) {
            SegueNavHost(
                appState = appState,
                startDestination = startDestination,
                playActions = playActions,
                onAddToPlaylist = { addToPlaylist = it },
                modifier = Modifier.fillMaxSize(),
            )
            SegueSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
    addToPlaylist?.let { songIds ->
        AddToPlaylistSheet(
            songIds = remember(songIds) { songIds.toImmutableList() },
            onDismissRequest = { addToPlaylist = null },
        )
    }
}

/**
 * The shell: navigation chrome, the player surface, and the nav host. Content draws edge to
 * edge and scrolls under the bottom chrome; screens read [LocalShellContentPadding].
 *
 * @param positionMillis collected only by a visible player surface (collecting it runs the position
 *   ticker) and read in the draw phase.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegueApp(
    appState: SegueAppState,
    player: ShellPlayerState,
    positionMillis: StateFlow<Long>,
    playerActions: ShellPlayerActions,
    modifier: Modifier = Modifier,
    sidebarPlaylists: ImmutableList<SidebarPlaylist> = persistentListOf(),
    onSidebarPlaylistClick: (String) -> Unit = {},
    navHost: @Composable (Modifier) -> Unit,
) {
    val destination = appState.currentDestination
    val fullScreen = appState.isFullScreen(destination)
    // Full-screen routes hide all chrome. The IME hides the phone bar and the mini players only;
    // the rail, sidebar and player bar stay. An empty queue hides the player (DESIGN.md §3).
    val imeVisible = WindowInsets.isImeVisible
    val hasTrack = player.current != null
    val chrome = ShellChrome(
        navigation = !fullScreen,
        bottomBar = !fullScreen && !imeVisible,
        miniPlayer = !fullScreen && !imeVisible && hasTrack,
        playerBar = !fullScreen && hasTrack,
        // On every screen but the full-screen ones; the Queue screen has its own banner.
        continueBar = !fullScreen && !imeVisible && player.remoteOffer != null,
    )
    val onTabClick = appState::navigateToTab

    // Back at the root of another tab returns to Home, then leaves the app.
    BackHandler(enabled = appState.currentTab != TopLevelDestination.Home && appState.isAtTabRoot(destination)) {
        appState.navigateToTab(TopLevelDestination.Home)
    }
    SystemBarAppearance(lightStatusIcons = SegueTheme.isDark || destination?.hasRoute(NowPlayingRoute::class) == true)

    val onOpenNowPlaying = remember(appState) { { appState.navigate { navigateToNowPlaying() } } }
    val onOpenQueue = remember(appState) { { appState.navigate { navigateToQueue() } } }
    val shellPlayer = remember(player, positionMillis, playerActions, onOpenNowPlaying, onOpenQueue) {
        ShellPlayer(player, positionMillis, playerActions, onOpenNowPlaying, onOpenQueue)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Text and icons without an explicit color follow the theme, as under a Surface.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            when (appState.shellLayout) {
                ShellLayout.Compact -> CompactShell(appState, chrome, shellPlayer, onTabClick, navHost)

                ShellLayout.Medium -> MediumShell(appState, chrome, shellPlayer, onTabClick, navHost)

                ShellLayout.Expanded -> ExpandedShell(
                    appState,
                    chrome,
                    shellPlayer,
                    onTabClick,
                    navHost,
                    sidebarPlaylists,
                    onSidebarPlaylistClick,
                )
            }
        }
    }
}

/** Which parts of the shell chrome are visible right now. */
@Immutable
private data class ShellChrome(
    /** Rail or sidebar. */
    val navigation: Boolean,
    /** The phone navigation bar. */
    val bottomBar: Boolean,
    /** Phone and medium. */
    val miniPlayer: Boolean,
    /** Expanded. */
    val playerBar: Boolean,
    /** "Continue from <device>": docks above the mini player or player bar, or alone with an empty queue. */
    val continueBar: Boolean,
)

/** Everything the player surfaces need, bundled so each shell layout takes one parameter. */
@Immutable
private class ShellPlayer(
    val state: ShellPlayerState,
    val positionMillis: StateFlow<Long>,
    val actions: ShellPlayerActions,
    val onOpenNowPlaying: () -> Unit,
    val onOpenQueue: () -> Unit,
)

@Composable
private fun CompactShell(
    appState: SegueAppState,
    chrome: ShellChrome,
    player: ShellPlayer,
    onTabClick: (TopLevelDestination) -> Unit,
    navHost: @Composable (Modifier) -> Unit,
) {
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        ShellContent(
            navHost,
            bottomChrome = if (chrome.bottomBar) {
                bottomChromeHeight(systemBottom, miniPlayer = chrome.miniPlayer, navBar = true, chrome.continueBar)
            } else {
                SegueSpacing.none
            },
        )
        Column(Modifier.align(Alignment.BottomCenter)) {
            ChromeVisibility(chrome.continueBar) { ShellContinueBar(player) }
            ChromeVisibility(chrome.miniPlayer) { ShellMiniPlayer(player) }
            ChromeVisibility(chrome.bottomBar) {
                SegueNavigationBar(currentTab = appState.currentTab, onTabClick = onTabClick)
            }
        }
    }
}

@Composable
private fun MediumShell(
    appState: SegueAppState,
    chrome: ShellChrome,
    player: ShellPlayer,
    onTabClick: (TopLevelDestination) -> Unit,
    navHost: @Composable (Modifier) -> Unit,
) {
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(Modifier.fillMaxSize()) {
        if (chrome.navigation) SegueNavigationRail(currentTab = appState.currentTab, onTabClick = onTabClick)
        Box(Modifier.weight(1f)) {
            val docked = chrome.miniPlayer || chrome.continueBar
            ShellContent(
                navHost,
                bottomChrome = if (docked) {
                    bottomChromeHeight(systemBottom, chrome.miniPlayer, navBar = false, chrome.continueBar)
                } else {
                    SegueSpacing.none
                },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (docked) {
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .navigationBarsPadding()
                        } else {
                            Modifier
                        },
                    ),
            ) {
                ChromeVisibility(chrome.continueBar) { ShellContinueBar(player) }
                ChromeVisibility(chrome.miniPlayer) { ShellMiniPlayer(player) }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ExpandedShell(
    appState: SegueAppState,
    chrome: ShellChrome,
    player: ShellPlayer,
    onTabClick: (TopLevelDestination) -> Unit,
    navHost: @Composable (Modifier) -> Unit,
    playlists: ImmutableList<SidebarPlaylist>,
    onPlaylistClick: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            if (chrome.navigation) {
                SegueSidebar(
                    currentTab = appState.currentTab,
                    onTabClick = onTabClick,
                    playlists = playlists,
                    onPlaylistClick = onPlaylistClick,
                )
            }
            Box(Modifier.weight(1f)) { ShellContent(navHost, bottomChrome = SegueSpacing.none) }
        }
        if (chrome.continueBar) {
            ShellContinueBar(
                player = player,
                modifier = if (chrome.playerBar) Modifier else Modifier.navigationBarsPadding(),
            )
        }
        // The player bar is permanent chrome on expanded width: only full-screen routes and an empty
        // queue hide it.
        if (chrome.playerBar) {
            ShellPlayerBar(
                player = player,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun ShellMiniPlayer(player: ShellPlayer, modifier: Modifier = Modifier) {
    val state = player.state
    val track = state.current ?: return
    val actions = player.actions
    val position = player.positionMillis.collectAsStateWithLifecycle()
    MiniPlayer(
        state = track,
        trackKey = state.currentTimelineIndex,
        progress = { progressOf(position.value, state.durationMillis) },
        onClick = player.onOpenNowPlaying,
        onPlayPauseClick = actions.onPlayPause,
        onNextClick = actions.onNext,
        previous = state.previous?.track,
        next = state.next?.track,
        onSwipeToPrevious = { state.previous?.let { actions.onSkipTo(it.timelineIndex) } },
        onSwipeToNext = { state.next?.let { actions.onSkipTo(it.timelineIndex) } },
        modifier = modifier,
    )
}

/** The compact "Continue from <device>" bar (DESIGN.md §4.8). */
@Composable
private fun ShellContinueBar(player: ShellPlayer, modifier: Modifier = Modifier) {
    val offer = player.state.remoteOffer ?: return
    ContinueFromDeviceBar(
        state = ContinueFromDeviceState(deviceName = offer.deviceName, detail = offer.currentTitle.orEmpty()),
        onContinueClick = player.actions.onContinueRemote,
        onDismissClick = player.actions.onDismissRemote,
        modifier = modifier,
    )
}

@Composable
private fun ShellPlayerBar(player: ShellPlayer, modifier: Modifier = Modifier) {
    val state = player.state
    val track = state.current ?: return
    val barState = PlayerBarState(
        track = track,
        durationSeconds = state.durationMillis / MILLIS_PER_SECOND,
        repeatMode = state.repeatMode,
        volume = 1f,
    )
    val actions = player.actions
    val position = player.positionMillis.collectAsStateWithLifecycle()
    val barActions = remember(actions, player.onOpenNowPlaying, player.onOpenQueue) {
        PlayerBarActions(
            onClick = player.onOpenNowPlaying,
            onPlayPauseClick = actions.onPlayPause,
            onPreviousClick = actions.onPrevious,
            onNextClick = actions.onNext,
            onRepeatClick = actions.onCycleRepeat,
            onLyricsClick = player.onOpenNowPlaying,
            onQueueClick = player.onOpenQueue,
            onMoreClick = player.onOpenNowPlaying,
            // System volume is the only volume; the bar's slider is not wired.
            onVolumeChange = {},
        )
    }
    PlayerBar(
        state = barState,
        positionSeconds = { position.value / MILLIS_PER_SECOND },
        actions = barActions,
        modifier = modifier,
    )
}

@Composable
private fun ShellContent(navHost: @Composable (Modifier) -> Unit, bottomChrome: Dp) {
    CompositionLocalProvider(LocalShellContentPadding provides PaddingValues(bottom = bottomChrome)) {
        navHost(Modifier.fillMaxSize())
    }
}

/** Mini player (and nav bar) slide and fade with `medium` when the IME or a full-screen route hides them. */
@Composable
private fun ChromeVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(SegueMotion.medium()) + fadeIn(SegueMotion.medium()),
        exit = shrinkVertically(SegueMotion.medium()) + fadeOut(SegueMotion.medium()),
        modifier = modifier.fillMaxWidth(),
    ) { content() }
}

private fun bottomChromeHeight(systemBottom: Dp, miniPlayer: Boolean, navBar: Boolean, continueBar: Boolean): Dp =
    (if (miniPlayer) SegueSizes.miniPlayer else SegueSpacing.none) +
        (if (continueBar) SegueSizes.miniPlayer else SegueSpacing.none) +
        (if (navBar) SegueSizes.navigationBar else SegueSpacing.none) +
        systemBottom

private const val MILLIS_PER_SECOND = 1_000L

/** Light status-bar icons over dark themes and over art (Now Playing), DESIGN.md §3. */
@Composable
private fun SystemBarAppearance(lightStatusIcons: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val darkTheme = SegueTheme.isDark
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !lightStatusIcons
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

internal fun WindowSizeClass.toShellLayout(): ShellLayout = when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> ShellLayout.Expanded
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> ShellLayout.Medium
    else -> ShellLayout.Compact
}
