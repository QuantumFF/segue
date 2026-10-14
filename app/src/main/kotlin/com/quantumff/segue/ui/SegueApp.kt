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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.window.core.layout.WindowSizeClass
import com.quantumff.segue.R
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MiniPlayer
import com.quantumff.segue.core.designsystem.component.MiniPlayerState
import com.quantumff.segue.core.designsystem.component.PlayerBar
import com.quantumff.segue.core.designsystem.component.PlayerBarActions
import com.quantumff.segue.core.designsystem.component.PlayerBarState
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.feature.nowplaying.navigation.NowPlayingRoute
import com.quantumff.segue.feature.nowplaying.navigation.navigateToNowPlaying
import com.quantumff.segue.navigation.SegueNavHost
import com.quantumff.segue.navigation.TopLevelDestination

/** App shell entry point: picks the layout for the window size (DESIGN.md §3). */
@Composable
fun SegueApp(modifier: Modifier = Modifier, windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo()) {
    val appState = rememberSegueAppState(shellLayout = windowAdaptiveInfo.windowSizeClass.toShellLayout())
    SegueApp(appState = appState, modifier = modifier)
}

/**
 * The shell: navigation chrome, the player surface, and the nav host. Content draws edge to
 * edge and scrolls under the bottom chrome; screens read [LocalShellContentPadding].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegueApp(appState: SegueAppState, modifier: Modifier = Modifier) {
    val destination = appState.currentDestination
    val fullScreen = appState.isFullScreen(destination)
    // Full-screen routes hide all chrome. The IME hides the phone bar and the mini players only;
    // the rail, sidebar and player bar stay (DESIGN.md §3).
    val imeVisible = WindowInsets.isImeVisible
    val chrome = ShellChrome(navigation = !fullScreen, player = !fullScreen && !imeVisible)
    val onTabClick = appState::navigateToTab

    // Back at the root of another tab returns to Home, then leaves the app.
    BackHandler(enabled = appState.currentTab != TopLevelDestination.Home && appState.isAtTabRoot(destination)) {
        appState.navigateToTab(TopLevelDestination.Home)
    }
    SystemBarAppearance(lightStatusIcons = SegueTheme.isDark || destination?.hasRoute(NowPlayingRoute::class) == true)

    // Placeholder until playback exists: a fixed track so the shell can be laid out and tested.
    val player = rememberPlaceholderPlayer()
    val onOpenNowPlaying = { appState.navigate { navigateToNowPlaying() } }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (appState.shellLayout) {
            ShellLayout.Compact -> CompactShell(appState, chrome, player, onTabClick, onOpenNowPlaying)
            ShellLayout.Medium -> MediumShell(appState, chrome, player, onTabClick, onOpenNowPlaying)
            ShellLayout.Expanded -> ExpandedShell(appState, chrome, player, onTabClick, onOpenNowPlaying)
        }
    }
}

/** Which parts of the shell chrome are visible right now. */
@Immutable
private data class ShellChrome(
    /** Bottom bar, rail or sidebar. */
    val navigation: Boolean,
    /** Mini player (phone and medium) or player bar (expanded). */
    val player: Boolean,
)

@Composable
private fun CompactShell(
    appState: SegueAppState,
    chrome: ShellChrome,
    player: PlayerBarState,
    onTabClick: (TopLevelDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    // On phones the bar hides with the IME too, so it follows the player flag.
    val visible = chrome.player
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        ShellContent(
            appState,
            bottomChrome = if (visible) bottomChromeHeight(systemBottom, navBar = true) else SegueSpacing.none,
        )
        Column(Modifier.align(Alignment.BottomCenter)) {
            ChromeVisibility(visible) {
                PlaceholderMiniPlayer(player = player, onOpenNowPlaying = onOpenNowPlaying)
            }
            ChromeVisibility(visible) {
                SegueNavigationBar(currentTab = appState.currentTab, onTabClick = onTabClick)
            }
        }
    }
}

@Composable
private fun MediumShell(
    appState: SegueAppState,
    chrome: ShellChrome,
    player: PlayerBarState,
    onTabClick: (TopLevelDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(Modifier.fillMaxSize()) {
        if (chrome.navigation) SegueNavigationRail(currentTab = appState.currentTab, onTabClick = onTabClick)
        Box(Modifier.weight(1f)) {
            ShellContent(
                appState,
                bottomChrome = if (chrome.player) {
                    bottomChromeHeight(
                        systemBottom,
                        navBar = false,
                    )
                } else {
                    SegueSpacing.none
                },
            )
            ChromeVisibility(chrome.player, Modifier.align(Alignment.BottomCenter)) {
                PlaceholderMiniPlayer(
                    player = player,
                    onOpenNowPlaying = onOpenNowPlaying,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun ExpandedShell(
    appState: SegueAppState,
    chrome: ShellChrome,
    player: PlayerBarState,
    onTabClick: (TopLevelDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val actions = remember(onOpenNowPlaying) { placeholderPlayerActions(onOpenNowPlaying) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            if (chrome.navigation) SegueSidebar(currentTab = appState.currentTab, onTabClick = onTabClick)
            Box(Modifier.weight(1f)) { ShellContent(appState, bottomChrome = SegueSpacing.none) }
        }
        // The player bar is permanent chrome on expanded width: only full-screen routes hide it.
        if (chrome.navigation) {
            PlayerBar(
                state = player,
                positionSeconds = { 0L },
                actions = actions,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding(),
            )
        }
    }
}

/** Mini player bound to the placeholder track; controls are no-ops until playback exists. */
@Composable
private fun PlaceholderMiniPlayer(player: PlayerBarState, onOpenNowPlaying: () -> Unit, modifier: Modifier = Modifier) {
    MiniPlayer(
        state = player.track,
        progress = { 0f },
        onClick = onOpenNowPlaying,
        onPlayPauseClick = {},
        onNextClick = {},
        modifier = modifier,
    )
}

@Composable
private fun ShellContent(appState: SegueAppState, bottomChrome: Dp) {
    CompositionLocalProvider(LocalShellContentPadding provides PaddingValues(bottom = bottomChrome)) {
        SegueNavHost(appState = appState, modifier = Modifier.fillMaxSize())
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

private fun bottomChromeHeight(systemBottom: Dp, navBar: Boolean): Dp =
    SegueSizes.miniPlayer + (if (navBar) SegueSizes.navigationBar else SegueSpacing.none) + systemBottom

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

@Composable
private fun rememberPlaceholderPlayer(): PlayerBarState {
    val title = stringResource(R.string.placeholder_track_title)
    val artist = stringResource(R.string.placeholder_track_artist)
    return remember(title, artist) {
        PlayerBarState(
            track = MiniPlayerState(title = title, artist = artist, coverArtId = null, isPlaying = false),
            durationSeconds = 0L,
            shuffleMode = ShuffleMode.Off,
            repeatMode = RepeatMode.Off,
            volume = 1f,
        )
    }
}

private fun placeholderPlayerActions(onOpenNowPlaying: () -> Unit) = PlayerBarActions(
    onClick = onOpenNowPlaying,
    onPlayPauseClick = {},
    onPreviousClick = {},
    onNextClick = {},
    onShuffleClick = {},
    onRepeatClick = {},
    onLyricsClick = {},
    onQueueClick = {},
    onMoreClick = {},
    onVolumeChange = {},
)

internal fun WindowSizeClass.toShellLayout(): ShellLayout = when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> ShellLayout.Expanded
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> ShellLayout.Medium
    else -> ShellLayout.Compact
}
