package com.quantumff.segue.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The feature card's text stays clear of its play button and is never clipped (DESIGN.md §4.1). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class FeaturedCarouselTest {
    @get:Rule
    val compose = createComposeRule()

    private val album = PreviewAlbums.first().copy(
        name = "The Long and Winding Title of an Album That Never Ends",
        artist = "An Artist With a Rather Long Name Indeed",
    )
    private val texts = listOf("FEATURED", album.name, album.artist!!, "2007 · 10 tracks")

    @Test
    fun textNeverRunsUnderThePlayButton() {
        setCarousel(fontScale = 1f)
        val button = compose.onNodeWithContentDescription("Play ${album.name}").getUnclippedBoundsInRoot()
        texts.forEach { text ->
            val bounds = textBounds(text)
            val overlaps = bounds.left < button.right && button.left < bounds.right &&
                bounds.top < button.bottom && button.top < bounds.bottom
            assertFalse("\"$text\" at $bounds runs under the play button at $button", overlaps)
        }
    }

    @Test
    fun cardGrowsToFitItsTextAtFontScaleTwo() {
        setCarousel(fontScale = 2f)
        val card = compose.onNode(hasClickAction() and hasText(album.name)).getUnclippedBoundsInRoot()
        assertTrue("card is ${card.height} tall", card.height > CARD_HEIGHT)
        texts.forEach { text ->
            val bounds = textBounds(text)
            assertTrue(
                "\"$text\" spans ${bounds.top}..${bounds.bottom}, outside the card ${card.top}..${card.bottom}",
                bounds.top >= card.top && bounds.bottom <= card.bottom,
            )
        }
    }

    private fun textBounds(text: String): DpRect =
        compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun setCarousel(fontScale: Float) {
        compose.setContent {
            Scaled(fontScale) { FeaturedCarousel(persistentListOf(album), onAlbumClick = {}, onPlayClick = {}) }
        }
    }
}

@Composable
private fun Scaled(fontScale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        SegueTheme(systemDark = true) { content() }
    }
}
