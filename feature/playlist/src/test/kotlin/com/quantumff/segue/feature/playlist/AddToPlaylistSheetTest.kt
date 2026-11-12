package com.quantumff.segue.feature.playlist

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddToPlaylistSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the sheet title is a heading`() {
        compose.setContent {
            SegueTheme {
                AddToPlaylistSheetContent(
                    uiState = AddToPlaylistUiState(),
                    onPlaylistClick = {},
                    onNewPlaylistClick = {},
                )
            }
        }

        compose.onNodeWithText("Add to playlist").assert(isHeading())
    }
}
