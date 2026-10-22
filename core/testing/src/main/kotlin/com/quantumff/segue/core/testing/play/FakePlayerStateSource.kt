package com.quantumff.segue.core.testing.play

import com.quantumff.segue.core.data.play.PlayerStateSource
import com.quantumff.segue.core.model.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A player that plays nothing: set [state] to whatever the test needs. [tickingState] is the same
 * flow, so a position set in [state] is the position a ticking reader sees.
 */
class FakePlayerStateSource(initial: PlayerUiState = PlayerUiState()) : PlayerStateSource {
    override val state = MutableStateFlow(initial)

    override val tickingState: StateFlow<PlayerUiState> get() = state
}
