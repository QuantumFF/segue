package com.quantumff.segue.feature.nowplaying.sheet

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.feature.nowplaying.LyricsUiState
import com.quantumff.segue.feature.nowplaying.NowPlayingActions
import com.quantumff.segue.feature.nowplaying.NowPlayingScreen
import com.quantumff.segue.feature.nowplaying.NowPlayingTrack
import com.quantumff.segue.feature.nowplaying.NowPlayingUiState
import com.quantumff.segue.feature.nowplaying.RelatedUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Speed in Now Playing's ⋮ menu: a sheet of preset speeds as radio rows. */
@RunWith(RobolectricTestRunner::class)
class SpeedSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val chosen = mutableListOf<Float>()

    @Test
    fun `the speed row names the speed and opens the presets with the current one selected`() {
        show(speed = 1.5f)

        compose.onAllNodesWithContentDescription("More options").onFirst().performClick()
        compose.onNodeWithText("Speed · 1.5×").performClick()

        assertThat(compose.onAllNodes(isRadioRow).fetchSemanticsNodes()).hasSize(7)
        compose.onNodeWithText("1.5×").assertIsSelected()
        compose.onNodeWithText("1×").assertIsNotSelected()
        compose.onNodeWithText("2×").performClick()
        assertThat(chosen).containsExactly(2f)
    }

    @Test
    fun `at normal speed the row is just Speed`() {
        show(speed = 1f)

        compose.onAllNodesWithContentDescription("More options").onFirst().performClick()

        compose.onNodeWithText("Speed").assertExists()
    }

    private val isRadioRow = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun show(speed: Float) {
        val actions = NowPlayingActions(
            onCollapse = {}, onPlayPause = {}, onPrevious = {}, onNext = {}, onSkipTo = {}, onSeek = {},
            onCycleRepeat = {}, onToggleFavorite = {}, onStartSleepTimer = {}, onCancelSleepTimer = {},
            onExtendSleepTimer = {},
            onSetSpeed = { chosen += it },
            onInstantMix = {}, onAlbumClick = {}, onArtistClick = {}, onSourceClick = {}, onAddToPlaylist = {},
            onVisiblePanelsChange = {}, onRetryLyrics = {}, onRetryRelated = {}, onPlayRelated = {},
            onPlayNext = {}, onAddToQueue = {}, onSaveQueueAsPlaylist = {}, onClearQueue = {},
        )
        compose.setContent {
            SegueTheme {
                NowPlayingScreen(
                    state = NowPlayingUiState(track = track, speed = speed),
                    positionMillis = { 0L },
                    lyrics = LyricsUiState.Loading,
                    activeLyricLine = { -1 },
                    related = RelatedUiState.Loading,
                    actions = actions,
                    queueContent = {},
                )
            }
        }
    }

    private val track = NowPlayingTrack(
        songId = "s1",
        timelineIndex = 0,
        title = "Song",
        artist = "Artist",
        artists = persistentListOf(),
        album = "Album",
        albumId = "al1",
        coverArtId = null,
        isStarred = false,
    )
}
