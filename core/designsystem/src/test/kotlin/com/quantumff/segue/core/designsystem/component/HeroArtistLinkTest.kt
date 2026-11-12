package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An album hero's artist name is a 48dp link that TalkBack announces as "Go to artist". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class HeroArtistLinkTest {
    @get:Rule
    val compose = createComposeRule()

    private var artistTaps = 0

    @Test
    fun `the artist link is a 48dp button that goes to the artist`() {
        show()

        compose.onNodeWithText("Radiohead")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher("click label is \"Go to artist\"") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Go to artist"
                },
            )
            .assertHeightIsAtLeast(SegueSizes.touchTarget)
            .performClick()

        assertThat(artistTaps).isEqualTo(1)
    }

    private fun show() {
        compose.setContent {
            SegueTheme {
                HeroHeader(
                    state = HeroHeaderState(
                        overline = "Album",
                        title = "OK Computer",
                        subtitle = "Radiohead",
                        metadata = "1997 · 12 tracks",
                        coverArtId = null,
                        circularArt = false,
                    ),
                    onSubtitleClick = { artistTaps++ },
                    modifier = Modifier.width(360.dp),
                ) {
                    PlayButton(onClick = {})
                }
            }
        }
    }
}
