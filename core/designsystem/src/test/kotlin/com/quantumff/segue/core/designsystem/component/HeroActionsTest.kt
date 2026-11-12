package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A hero's action row wraps onto another line when it can't fit, so no button squeezes (DESIGN.md §5.4). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class HeroActionsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `more actions than fit on a narrow phone wrap and keep their size`() {
        show(width = 360)

        repeat(ACTIONS) { index ->
            compose.onNodeWithContentDescription("Action $index")
                .assertWidthIsEqualTo(SegueSizes.touchTarget)
                .assertHeightIsEqualTo(SegueSizes.touchTarget)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun `the short hero's actions wrap too`() {
        show(width = 800)

        repeat(ACTIONS) { index ->
            compose.onNodeWithContentDescription("Action $index").assertWidthIsEqualTo(SegueSizes.touchTarget)
        }
    }

    @Test
    fun `the hero title is a heading`() {
        show(width = 360)

        compose.onNodeWithText("Radiohead").assert(isHeading())
    }

    private fun show(width: Int) {
        compose.setContent {
            SegueTheme {
                HeroHeader(
                    state = HeroHeaderState(
                        overline = "Artist",
                        title = "Radiohead",
                        subtitle = null,
                        metadata = "9 albums · 30 tracks",
                        coverArtId = null,
                        circularArt = true,
                    ),
                    modifier = Modifier.width(width.dp),
                ) {
                    PlayButton(onClick = {})
                    repeat(ACTIONS) { index ->
                        SegueIconButton(icon = SegueIcons.Shuffle, contentDescription = "Action $index", onClick = {})
                    }
                }
            }
        }
    }

    private companion object {
        /** Enough 48 buttons that a single row overflows even beside the short hero's art. */
        const val ACTIONS = 12
    }
}
