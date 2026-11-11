package com.quantumff.segue.feature.artist

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Artist detail goldens on a 360 phone: every action button shows, Favorite included, and at the
 * largest font size the hero's actions wrap rather than squeeze (DESIGN.md §4.4, §5.4).
 * Record with `./gradlew :feature:artist:recordRoborazziDebug`, check with `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ArtistScreenScreenshotTest {
    @Test
    fun artistDetail() {
        listOf(true, false).forEach { dark -> capture("artist_detail_${if (dark) "dark" else "light"}", dark) }
    }

    @Test
    fun artistDetailFontScale2() = capture("artist_detail_font_scale_2_dark", dark = true, fontScale = 2f)

    private fun capture(name: String, dark: Boolean, fontScale: Float = 1f) {
        captureRoboImage("src/test/screenshots/$name.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SegueTheme(systemDark = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        ArtistScreen(
                            state = PreviewArtistState,
                            onBackClick = {},
                            onAlbumClick = {},
                            onArtistClick = {},
                            onPlay = {},
                            onHeroPlay = {},
                            onPlayTopSong = {},
                            onShuffleAlbums = {},
                            onInstantMix = {},
                            onToggleStar = {},
                            onRetry = {},
                            onMessageDismiss = {},
                        )
                    }
                }
            }
        }
    }
}
