package com.quantumff.segue.feature.album

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A phone in landscape (DESIGN.md §5.4): an 800 × 360 window, with a 24 status bar and the 64 mini
 * player over the bottom of the page, shows at least one whole track under the short hero.
 *
 * Robolectric draws no status bar, so the hero is 24 shorter than on a phone; the page gets 24 less
 * height instead, which leaves the first track at the same place relative to the visible area.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h360dp-land-xhdpi")
class AlbumShortWindowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aSingleDiscAlbumShowsItsFirstTrackWhole() {
        val visible = 360.dp - STATUS_BAR - SegueSizes.miniPlayer
        compose.setContent {
            SegueTheme(systemDark = true) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(visible),
                ) {
                    AlbumScreen(
                        state = SingleDisc,
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
                    )
                }
            }
        }

        val firstTrack = compose.onNodeWithText("15 Step").getUnclippedBoundsInRoot()
        // The row is 56 tall around its centered title.
        assertThat((firstTrack.top + firstTrack.bottom) / 2 + SegueSizes.trackRow / 2).isAtMost(visible)
    }

    private companion object {
        val STATUS_BAR = 24.dp

        val SingleDisc = run {
            val preview = PreviewAlbumState
            val album = preview.album.copy(discTitles = persistentListOf())
            val songs = preview.songs.mapIndexed { index, song -> song.copy(discNumber = 1, track = index + 1) }
                .toImmutableList()
            preview.copy(album = album, songs = songs, discs = groupDiscs(album, songs))
        }
    }
}
