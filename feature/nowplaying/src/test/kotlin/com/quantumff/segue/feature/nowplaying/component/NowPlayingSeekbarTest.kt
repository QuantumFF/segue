package com.quantumff.segue.feature.nowplaying.component

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NowPlayingSeekbarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the seekbar announces the elapsed time of the total`() {
        show()

        compose.onNodeWithContentDescription("Seek")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1:52 of 4:15"))
    }

    @Test
    fun `the seekbar keeps its 32 slot but is 48 tall to touch`() {
        show()

        compose.onNodeWithContentDescription("Seek").assertHeightIsAtLeast(48.dp)
        val seekbar = compose.onNodeWithTag(SEEKBAR).getBoundsInRoot()
        val seek = compose.onNodeWithContentDescription("Seek").getBoundsInRoot()
        // The touch strip overhangs the slot above the times by 8 on each side.
        assertThat(seek.top).isEqualTo(seekbar.top - 8.dp)
    }

    @Test
    fun `the time toggle is a button with a 48 target inside the seekbar`() {
        show()

        val toggle = compose.onNode(clickLabel("Show remaining time"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        assertThat(toggle.getBoundsInRoot().right).isAtMost(compose.onNodeWithTag(SEEKBAR).getBoundsInRoot().right)
    }

    @Test
    fun `the time toggle switches between total and remaining time`() {
        show()

        compose.onNode(clickLabel("Show remaining time")).performClick()

        compose.onNode(clickLabel("Show total time")).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `the time toggle's target does not make the times row taller`() {
        show()

        val seekbar = compose.onNodeWithTag(SEEKBAR).getBoundsInRoot()
        val elapsed = compose.onNodeWithText("1:52").getBoundsInRoot()
        // The row is as tall as the elapsed time and its padding; the line takes 32 above it.
        assertThat(seekbar.bottom - seekbar.top - 32.dp).isEqualTo(elapsed.bottom - elapsed.top + 8.dp)
    }

    private fun show() {
        compose.setContent {
            SegueTheme(systemDark = true) {
                NowPlayingSeekbar(
                    positionMillis = { 112_000L },
                    durationMillis = 255_000L,
                    isPlaying = false,
                    quality = "FLAC",
                    onSeek = {},
                    modifier = Modifier.width(320.dp).testTag(SEEKBAR),
                )
            }
        }
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label is \"$label\"") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private companion object {
        const val SEEKBAR = "seekbar"
    }
}
