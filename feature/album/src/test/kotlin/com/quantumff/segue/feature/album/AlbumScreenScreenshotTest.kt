package com.quantumff.segue.feature.album

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Album detail goldens, dark and light.
 * Record with `./gradlew :feature:album:recordRoborazziDebug`, check with `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xhdpi")
class AlbumScreenScreenshotTest {
    @Test
    fun albumDetail() {
        listOf(true, false).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/album_detail_$theme.png") {
                SegueTheme(systemDark = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
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
                        )
                    }
                }
            }
        }
    }
}
