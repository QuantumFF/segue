package com.quantumff.segue.feature.album

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A multi-disc album's disc headers are headings, so TalkBack can jump between discs. */
@RunWith(RobolectricTestRunner::class)
class AlbumDiscHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `disc headers are headings`() {
        compose.setContent {
            SegueTheme(systemDark = true) {
                AlbumScreen(
                    state = PreviewAlbumState,
                    onBackClick = {},
                    onAlbumClick = {},
                    onArtistClick = {},
                    onGenreClick = {},
                    onPlay = { _, _ -> },
                    onHeroPlay = {},
                    onShuffleAlbums = {},
                    onToggleStar = {},
                    onRatingChange = {},
                    onRetry = {},
                    onMessageDismiss = {},
                    onOfflineModeSettingClick = {},
                )
            }
        }

        listOf("Disc 1", "Disc 2 · Bonus").forEach { header ->
            compose.onNode(hasScrollToKeyAction()).performScrollToNode(hasText(header))
            compose.onNodeWithText(header).assert(isHeading())
        }
    }
}
