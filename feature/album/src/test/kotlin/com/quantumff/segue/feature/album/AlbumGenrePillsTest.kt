package com.quantumff.segue.feature.album

import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Album
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The album page shows a genre pill for every genre of the album. */
@RunWith(RobolectricTestRunner::class)
class AlbumGenrePillsTest {
    @get:Rule
    val compose = createComposeRule()

    private val openedGenres = mutableListOf<String>()

    private val previewAlbum = (PreviewAlbumState as AlbumUiState.Success).album

    @Test
    fun `an album with several genres shows a pill for each, and each opens its genre`() {
        show(previewAlbum.copy(genre = "Rock", genres = persistentListOf("Rock", "Ambient", "Jazz")))

        listOf("Rock", "Ambient", "Jazz").forEach { genre ->
            compose.onNode(hasScrollToKeyAction()).performScrollToNode(hasText(genre))
            compose.onNodeWithText(genre).performClick()
        }

        assertThat(openedGenres).containsExactly("Rock", "Ambient", "Jazz").inOrder()
    }

    @Test
    fun `an album with only the single genre field shows that one pill`() {
        // The preview album sets only `genre = "Alternative"`.
        show(previewAlbum)

        compose.onNode(hasScrollToKeyAction()).performScrollToNode(hasText("Alternative"))
        compose.onNodeWithText("Alternative").performClick()

        assertThat(openedGenres).containsExactly("Alternative")
    }

    private fun show(album: Album) {
        compose.setContent {
            SegueTheme(systemDark = true) {
                AlbumScreen(
                    state = (PreviewAlbumState as AlbumUiState.Success).copy(album = album),
                    onBackClick = {},
                    onAlbumClick = {},
                    onArtistClick = {},
                    onGenreClick = { openedGenres += it },
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
    }
}
