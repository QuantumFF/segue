package com.quantumff.segue.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.quantumff.segue.core.data.snackbar.Snackbars
import com.quantumff.segue.core.designsystem.component.MiniPlayerState
import com.quantumff.segue.playback.ShellPlayerActions
import com.quantumff.segue.playback.ShellPlayerState
import kotlinx.coroutines.flow.MutableStateFlow

internal const val TEST_TRACK = "Test Track"

internal val testPlayer = ShellPlayerState(
    current = MiniPlayerState(title = TEST_TRACK, artist = "Test Artist", coverArtId = null, isPlaying = false),
    durationMillis = 200_000,
)

/**
 * The shell with a fixed player state, no-op player commands, [snackbars], and [nowPlaying], by
 * default a stand-in that only says "Now playing".
 */
@Composable
internal fun TestShell(
    appState: SegueAppState,
    player: ShellPlayerState = testPlayer,
    snackbars: Snackbars = remember { Snackbars() },
    nowPlaying: @Composable () -> Unit = { if (appState.isNowPlayingOpen) Text("Now playing") },
) {
    SegueApp(
        appState = appState,
        player = player,
        snackbars = snackbars,
        positionMillis = remember { MutableStateFlow(50_000L) },
        playerActions = ShellPlayerActions.None,
        nowPlaying = nowPlaying,
    ) { TestNavHost(appState, it) }
}
