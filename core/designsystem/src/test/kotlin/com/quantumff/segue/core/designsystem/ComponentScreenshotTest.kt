package com.quantumff.segue.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.quantumff.segue.core.designsystem.component.AlbumCard
import com.quantumff.segue.core.designsystem.component.AlbumPlayButtons
import com.quantumff.segue.core.designsystem.component.ArtistCard
import com.quantumff.segue.core.designsystem.component.ContextMenuContent
import com.quantumff.segue.core.designsystem.component.ContextMenuHeader
import com.quantumff.segue.core.designsystem.component.ContextMenuItem
import com.quantumff.segue.core.designsystem.component.ContinueFromDeviceBanner
import com.quantumff.segue.core.designsystem.component.ContinueFromDeviceState
import com.quantumff.segue.core.designsystem.component.DetailTopBar
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.HeroHeader
import com.quantumff.segue.core.designsystem.component.HeroHeaderState
import com.quantumff.segue.core.designsystem.component.MiniPlayer
import com.quantumff.segue.core.designsystem.component.PickerSheetContent
import com.quantumff.segue.core.designsystem.component.Pill
import com.quantumff.segue.core.designsystem.component.PlayButton
import com.quantumff.segue.core.designsystem.component.SectionHeader
import com.quantumff.segue.core.designsystem.component.SegueFilterChip
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SegueSortChip
import com.quantumff.segue.core.designsystem.component.TopLevelTopBar
import com.quantumff.segue.core.designsystem.component.TrackRow
import com.quantumff.segue.core.designsystem.component.TrackRowLeading
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot goldens for the key components, dark and light.
 * Record with `./gradlew :core:designsystem:recordRoborazziDebug`, check with `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ComponentScreenshotTest {
    @Test
    fun cards() = capture("cards") {
        Row(Modifier.padding(SegueSpacing.lg), horizontalArrangement = Arrangement.spacedBy(SegueSpacing.gutter)) {
            AlbumCard(state = PreviewData.album, onClick = {}, modifier = Modifier.width(SegueSizes.albumCard))
            ArtistCard(state = PreviewData.artist, onClick = {}, modifier = Modifier.width(SegueSizes.artistCircle))
        }
    }

    @Test
    fun trackRows() = capture("track_rows") {
        Column {
            SectionHeader(title = "Top songs", onSeeAllClick = {})
            // Paused: an infinite equalizer animation never lets the capture go idle.
            PreviewData.tracks.forEach { TrackRow(state = it.copy(isPlaying = false), onClick = {}, onMoreClick = {}) }
            PreviewData.tracks.take(2).forEach {
                TrackRow(state = it, onClick = {}, leading = TrackRowLeading.Number)
            }
        }
    }

    @Test
    fun heroHeader() = capture("hero_header") {
        HeroHeader(
            state = HeroHeaderState(
                overline = "Album",
                title = "In Rainbows",
                subtitle = "Radiohead",
                metadata = "2007 · 10 tracks · 42 min",
                coverArtId = null,
            ),
        ) {
            PlayButton(onClick = {})
            SegueIconButton(icon = SegueIcons.Shuffle, contentDescription = null, onClick = {})
            SegueIconButton(icon = SegueIcons.Heart, contentDescription = null, onClick = {})
        }
    }

    /** The page is the playing queue source: the collapsed top bar's play and the hero's Play show Pause. */
    @Test
    fun heroPlayingSource() = capture("hero_playing_source") {
        Column(verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg)) {
            DetailTopBar(title = "In Rainbows", collapsed = true, onBackClick = {}, onPlayClick = {}, isPlaying = true)
            Row(
                Modifier.padding(horizontal = SegueSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
            ) {
                PlayButton(onClick = {}, isPlaying = true)
                SegueIconButton(icon = SegueIcons.Shuffle, contentDescription = null, onClick = {})
            }
        }
    }

    @Test
    fun playerChrome() = capture("player_chrome") {
        Column(verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg)) {
            TopLevelTopBar(title = "Home")
            MiniPlayer(
                state = PreviewData.miniPlayer,
                progress = { 0.4f },
                onClick = {},
                onPlayPauseClick = {},
                onNextClick = {},
            )
            AlbumPlayButtons(
                onPlay = { _, _ -> },
                modifier = Modifier.padding(horizontal = SegueSpacing.lg),
            )
            ContinueFromDeviceBanner(
                state = ContinueFromDeviceState(deviceName = "Feishin", detail = "Nude · 2:31 · 5 min ago"),
                onContinueClick = {},
                onDismissClick = {},
                modifier = Modifier.padding(horizontal = SegueSpacing.lg),
            )
            Row(
                Modifier.padding(horizontal = SegueSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
            ) {
                SegueFilterChip(label = "Favorites", selected = true, onClick = {})
                SegueFilterChip(label = "Downloaded", selected = true, excluded = true, onClick = {})
                Pill(label = "Rock")
            }
        }
    }

    @Test
    fun sheetAndStates() = capture("sheet_and_states") {
        Column {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                ContextMenuContent(
                    header = ContextMenuHeader(title = "Nude", subtitle = "Radiohead", coverArtId = null),
                    items = persistentListOf(
                        ContextMenuItem(id = "next", label = "Play next", icon = SegueIcons.PlayNext),
                        ContextMenuItem(id = "queue", label = "Add to queue", icon = SegueIcons.AddToQueue),
                        ContextMenuItem(id = "delete", label = "Delete", icon = SegueIcons.Remove, destructive = true),
                    ),
                    onItemClick = {},
                )
            }
            EmptyState(
                icon = SegueIcons.Download,
                title = "No downloads yet",
                message = "Pin albums or playlists to listen offline",
                actionLabel = "Browse",
            )
        }
    }

    @Test
    fun pickerSheet() = capture("picker_sheet") {
        Column {
            Row(
                Modifier.padding(horizontal = SegueSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegueSortChip(label = "Recently added", onClick = {})
                SegueFilterChip(label = "Favorites", selected = false, onClick = {})
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                val options = persistentListOf("Recently added", "Name", "Artist", "Year")
                PickerSheetContent(
                    title = "Sort",
                    options = options,
                    selected = options.first(),
                    label = { it },
                    onSelect = {},
                )
            }
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        listOf(true, false).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$theme.png") {
                SegueTheme(systemDark = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }
}
