package com.quantumff.segue.feature.nowplaying

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.designsystem.theme.SegueThemeConfig
import com.quantumff.segue.core.designsystem.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The containers inside Now Playing's panels (DESIGN.md §4.7): the lyrics Sync chip floats over lyric
 * lines and a dragged queue row (filled with `surfaceContainerHighest`) over the rows beneath, so no
 * container lets what's under it show through, in any theme. Dark and Black keep their own containers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class NowPlayingOnArtColorsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lightPanelContainersAreOpaque() {
        containers(ThemeMode.Light).inPanel.forEach { (role, color) -> assertEquals("$role alpha", 1f, color.alpha) }
    }

    @Test
    fun darkPanelContainersAreTheThemesOwn() {
        val containers = containers(ThemeMode.Dark)
        assertEquals(containers.inTheme, containers.inPanel)
    }

    @Test
    fun blackPanelContainersAreTheThemesOwn() {
        val containers = containers(ThemeMode.Black)
        assertEquals(containers.inTheme, containers.inPanel)
    }

    private class Containers(val inTheme: Map<String, Color>, val inPanel: Map<String, Color>)

    private fun containers(mode: ThemeMode): Containers {
        var inTheme = emptyMap<String, Color>()
        var inPanel = emptyMap<String, Color>()
        compose.setContent {
            SegueTheme(config = SegueThemeConfig(themeMode = mode), systemDark = true) {
                inTheme = MaterialTheme.colorScheme.containers()
                NowPlayingScreen(
                    state = PreviewState,
                    positionMillis = { 112_000L },
                    lyrics = LyricsUiState.Loading,
                    activeLyricLine = { -1 },
                    related = RelatedUiState.Loading,
                    actions = NowPlayingActions.None,
                    queueContent = { inPanel = MaterialTheme.colorScheme.containers() },
                )
            }
        }
        compose.onNode(hasText("Queue").or(hasContentDescription("Queue"))).performClick()
        compose.waitForIdle()
        return Containers(inTheme, inPanel)
    }

    private fun ColorScheme.containers() = mapOf(
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "inverseSurface" to inverseSurface,
    )
}
