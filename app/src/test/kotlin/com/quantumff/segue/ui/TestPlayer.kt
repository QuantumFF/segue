package com.quantumff.segue.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.quantumff.segue.core.designsystem.component.MiniPlayerState
import com.quantumff.segue.playback.ShellPlayerActions
import com.quantumff.segue.playback.ShellPlayerState
import kotlinx.coroutines.flow.MutableStateFlow

internal const val TEST_TRACK = "Test Track"

internal val testPlayer = ShellPlayerState(
    current = MiniPlayerState(title = TEST_TRACK, artist = "Test Artist", coverArtId = null, isPlaying = false),
    durationMillis = 200_000,
)

/** The shell with a fixed player state and no-op player commands. */
@Composable
internal fun TestShell(appState: SegueAppState, player: ShellPlayerState = testPlayer) {
    SegueApp(
        appState = appState,
        player = player,
        positionMillis = remember { MutableStateFlow(50_000L) },
        playerActions = ShellPlayerActions.None,
    ) { TestNavHost(appState, it) }
}
