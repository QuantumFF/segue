package com.quantumff.segue.core.data.play

import com.quantumff.segue.core.model.PlayerUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * What the player is doing, read-only: the current item, whether it plays, the queue and the rest
 * of [PlayerUiState]. Screens that show player state read it here; commands go elsewhere
 * ([PlayIntents] to queue music, [PlayerTransport] to pause and resume, the playback controller for
 * the rest of transport and queue edits).
 *
 * The playback module implements it over the playback session. Observing either flow connects to
 * the session; while it is disconnected both hold an empty [PlayerUiState]. Tests use the fake in
 * `core:testing`, whose state they set.
 */
interface PlayerStateSource {
    /**
     * Player state as of the last player event. Its [PlayerUiState.positionMillis] does not tick,
     * so observing it never runs the position ticker. Read this unless a position is drawn.
     */
    val state: StateFlow<PlayerUiState>

    /**
     * [state] plus a position that ticks about twice a second while music plays. Collect it only
     * where a position is drawn, while visible: collecting it is what runs the ticker.
     */
    val tickingState: StateFlow<PlayerUiState>
}
