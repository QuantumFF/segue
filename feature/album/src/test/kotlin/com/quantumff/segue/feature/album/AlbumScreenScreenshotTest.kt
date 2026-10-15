package com.quantumff.segue.feature.album

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.PlayActions
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
                            playActions = PlayActions.None,
                            onBackClick = {},
                            onAlbumClick = {},
                            onArtistClick = {},
                            onGenreClick = {},
                            onAddToPlaylist = {},
                            onPlay = { _, _ -> },
                            onShuffleAlbums = {},
                            onToggleStar = {},
                            onToggleSongStar = {},
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
