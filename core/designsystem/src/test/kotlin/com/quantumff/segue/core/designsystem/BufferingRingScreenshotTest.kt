package com.quantumff.segue.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.component.MiniPlayer
import com.quantumff.segue.core.designsystem.component.PlayerBar
import com.quantumff.segue.core.designsystem.component.PlayerBarActions
import com.quantumff.segue.core.designsystem.component.PlayerBarState
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.RepeatMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot goldens for the play/pause buttons while the stream loads, dark and light. Same
 * commands as [ComponentScreenshotTest]. The ring spins forever, so the clock is held still: a
 * capture would otherwise never go idle.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w840dp-h640dp-xhdpi")
class BufferingRingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bufferingRing() {
        var dark by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SegueTheme(systemDark = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg)) {
                        MiniPlayer(
                            state = buffering,
                            progress = { 0.4f },
                            onClick = {},
                            onPlayPauseClick = {},
                            onNextClick = {},
                        )
                        PlayerBar(
                            state = PlayerBarState(
                                track = buffering,
                                durationSeconds = 255,
                                repeatMode = RepeatMode.All,
                                volume = 0.7f,
                            ),
                            positionSeconds = { 112 },
                            actions = noActions,
                        )
                    }
                }
            }
        }
        listOf(true, false).forEach { isDark ->
            dark = isDark
            compose.waitForIdle()
            // Past the 300ms delay, to a fixed point in the ring's spin.
            compose.mainClock.advanceTimeBy(RING_SHOWN_MILLIS)
            val theme = if (isDark) "dark" else "light"
            compose.onRoot().captureRoboImage("src/test/screenshots/buffering_ring_$theme.png")
        }
    }

    private val buffering = PreviewData.miniPlayer.copy(isPlaying = false, isBuffering = true)

    private val noActions = PlayerBarActions(
        onClick = {},
        onPlayPauseClick = {},
        onPreviousClick = {},
        onNextClick = {},
        onRepeatClick = {},
        onLyricsClick = {},
        onQueueClick = {},
        onMoreClick = {},
        onVolumeChange = {},
    )

    private companion object {
        const val RING_SHOWN_MILLIS = 800L
    }
}
