package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.RepeatMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp")
class PlayerBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val seeks = mutableListOf<Long>()
    private var repeatMode by mutableStateOf(RepeatMode.Off)

    @Test
    fun `the seek line exposes setProgress, which seeks the current song`() {
        show()

        compose.onNodeWithContentDescription("Seek")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }

        assertThat(seeks).containsExactly(DURATION_MILLIS / 2)
    }

    @Test
    fun `the seek line announces the elapsed time of the total`() {
        show()

        compose.onNodeWithContentDescription("Seek")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1:00 of 4:00"))
    }

    @Test
    fun `the seek line is thin but 48 tall to touch`() {
        show()

        compose.onNodeWithContentDescription("Seek").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `a tap near the top of the 48 touch height still seeks`() {
        show()

        // 2dp from the top: outside the line's 24dp slot in the layout, inside its touch height. At
        // the line's start, clear of the transport buttons, which win where they overlap it.
        compose.onNodeWithContentDescription("Seek").performTouchInput { click(Offset(left + 1f, top + 2.dp.toPx())) }
        compose.waitForIdle()

        assertThat(seeks).hasSize(1)
        assertThat(seeks.single()).isAtMost(DURATION_MILLIS / 10)
    }

    @Test
    fun `dragging the seek line seeks once, where the drag ends`() {
        show()

        compose.onNodeWithContentDescription("Seek").performTouchInput { swipeRight(startX = left, endX = right) }
        compose.waitForIdle()

        assertThat(seeks).hasSize(1)
        assertThat(seeks.single()).isAtLeast(DURATION_MILLIS * 9 / 10)
    }

    @Test
    fun `the track area's action is Open Now Playing`() {
        show()

        compose.onNode(hasText(PreviewData.miniPlayer.title).and(clickLabel("Open Now Playing")))
            .assertExists()
    }

    @Test
    fun `repeat is named Repeat and announces its mode`() {
        show()

        listOf(RepeatMode.Off to "Off", RepeatMode.All to "All", RepeatMode.One to "One").forEach { (mode, state) ->
            repeatMode = mode
            compose.waitForIdle()
            compose.onNode(hasContentDescription("Repeat"))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
        }
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                PlayerBar(
                    state = PlayerBarState(
                        track = PreviewData.miniPlayer.copy(isPlaying = false),
                        durationMillis = DURATION_MILLIS,
                        repeatMode = repeatMode,
                        volume = 0.7f,
                    ),
                    positionMillis = { POSITION_MILLIS },
                    actions = PlayerBarActions(
                        onClick = {},
                        onPlayPauseClick = {},
                        onPreviousClick = {},
                        onNextClick = {},
                        onRepeatClick = {},
                        onLyricsClick = {},
                        onQueueClick = {},
                        onMoreClick = {},
                        onVolumeChange = {},
                        onSeek = { seeks += it },
                    ),
                    modifier = Modifier.width(1280.dp),
                )
            }
        }
    }

    private companion object {
        const val DURATION_MILLIS = 240_000L
        const val POSITION_MILLIS = 60_000L
    }
}
