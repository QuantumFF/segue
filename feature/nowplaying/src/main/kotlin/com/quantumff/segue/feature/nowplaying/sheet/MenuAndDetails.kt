package com.quantumff.segue.feature.nowplaying.sheet

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.quantumff.segue.core.designsystem.component.ContextMenuHeader
import com.quantumff.segue.core.designsystem.component.ContextMenuItem
import com.quantumff.segue.core.designsystem.component.ContextMenuSheet
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.feature.nowplaying.NowPlayingTrack
import com.quantumff.segue.feature.nowplaying.R
import com.quantumff.segue.feature.nowplaying.TrackDetails
import com.quantumff.segue.feature.nowplaying.formatKiloHertz
import java.util.Locale
import kotlinx.collections.immutable.toImmutableList

/** Rows of the Now Playing ⋮ menu. */
internal enum class NowPlayingMenuAction {
    SleepTimer,
    InstantMix,
    AddToPlaylist,
    GoToAlbum,
    GoToArtist,
    Details,
}

/** The ⋮ menu: a context sheet about the current track (DESIGN.md §4.7, §5.7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingMenu(
    track: NowPlayingTrack,
    onAction: (NowPlayingMenuAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val labels = NowPlayingMenuAction.entries.associateWith { stringResource(it.label) }
    val items = remember(track.albumId, track.artists, labels) {
        NowPlayingMenuAction.entries
            .filter { action ->
                when (action) {
                    NowPlayingMenuAction.GoToAlbum -> track.albumId != null
                    NowPlayingMenuAction.GoToArtist -> track.artists.isNotEmpty()
                    else -> true
                }
            }
            .map { ContextMenuItem(id = it.name, label = labels.getValue(it), icon = it.icon) }
            .toImmutableList()
    }
    ContextMenuSheet(
        header = ContextMenuHeader(
            title = track.title,
            subtitle = listOfNotNull(track.artist, track.album).joinToString(" · ").ifEmpty { null },
            coverArtId = track.coverArtId,
        ),
        items = items,
        onItemClick = { item ->
            onDismissRequest()
            onAction(NowPlayingMenuAction.valueOf(item.id))
        },
        onDismissRequest = onDismissRequest,
    )
}

private val NowPlayingMenuAction.label: Int
    get() = when (this) {
        NowPlayingMenuAction.SleepTimer -> R.string.feature_nowplaying_menu_sleep_timer
        NowPlayingMenuAction.InstantMix -> com.quantumff.segue.core.designsystem.R.string.ds_action_instant_mix
        NowPlayingMenuAction.AddToPlaylist -> com.quantumff.segue.core.designsystem.R.string.ds_action_add_to_playlist
        NowPlayingMenuAction.GoToAlbum -> com.quantumff.segue.core.designsystem.R.string.ds_action_go_to_album
        NowPlayingMenuAction.GoToArtist -> com.quantumff.segue.core.designsystem.R.string.ds_action_go_to_artist
        NowPlayingMenuAction.Details -> R.string.feature_nowplaying_menu_details
    }

private val NowPlayingMenuAction.icon
    get() = when (this) {
        NowPlayingMenuAction.SleepTimer -> SegueIcons.Timer
        NowPlayingMenuAction.InstantMix -> SegueIcons.Related
        NowPlayingMenuAction.AddToPlaylist -> SegueIcons.AddToPlaylist
        NowPlayingMenuAction.GoToAlbum -> SegueIcons.Disc3
        NowPlayingMenuAction.GoToArtist -> SegueIcons.Artist
        NowPlayingMenuAction.Details -> SegueIcons.Info
    }

/** File details: format, bit rate, sample rate, bit depth, channels, size, ReplayGain, path. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackDetailsSheet(details: TrackDetails?, onDismissRequest: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SegueShapes.sheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = SegueSizes.noElevation,
    ) {
        TrackDetailsContent(
            details = details,
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
internal fun TrackDetailsContent(details: TrackDetails?, modifier: Modifier = Modifier) {
    val rows = detailRows(details)
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.feature_nowplaying_details_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm)
                .semantics { heading() },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.feature_nowplaying_details_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(SegueSpacing.lg),
            )
        }
        SelectionContainer {
            Column {
                rows.forEach { (label, value) ->
                    Column(Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(SegueSpacing.sm))
    }
}

@Composable
private fun detailRows(details: TrackDetails?): List<Pair<String, String>> {
    if (details == null) return emptyList()
    val context = LocalContext.current
    val rows = mutableListOf<Pair<String, String>>()
    fun add(label: Int, value: String?) {
        if (value != null) rows += context.getString(label) to value
    }
    add(R.string.feature_nowplaying_details_format, details.format)
    add(
        R.string.feature_nowplaying_details_bitrate,
        details.bitRateKbps?.let { context.getString(R.string.feature_nowplaying_details_bitrate_value, it) },
    )
    add(
        R.string.feature_nowplaying_details_sample_rate,
        details.samplingRateHz?.let {
            context.getString(R.string.feature_nowplaying_details_sample_rate_value, formatKiloHertz(it))
        },
    )
    add(
        R.string.feature_nowplaying_details_bit_depth,
        details.bitDepth?.let { context.getString(R.string.feature_nowplaying_details_bit_depth_value, it) },
    )
    add(R.string.feature_nowplaying_details_channels, details.channelCount?.toString())
    add(R.string.feature_nowplaying_details_size, details.sizeBytes?.let { Formatter.formatFileSize(context, it) })
    details.replayGain?.let { gain -> addReplayGain(gain) { label, value -> add(label, value) } }
    add(R.string.feature_nowplaying_details_path, details.path)
    return rows
}

private inline fun addReplayGain(gain: ReplayGain, add: (Int, String?) -> Unit) {
    add(R.string.feature_nowplaying_details_track_gain, gain.trackGain?.let(::formatGain))
    add(R.string.feature_nowplaying_details_track_peak, gain.trackPeak?.let(::formatPeak))
    add(R.string.feature_nowplaying_details_album_gain, gain.albumGain?.let(::formatGain))
    add(R.string.feature_nowplaying_details_album_peak, gain.albumPeak?.let(::formatPeak))
}

private fun formatGain(db: Float): String = String.format(Locale.ROOT, "%+.2f dB", db)

private fun formatPeak(peak: Float): String = String.format(Locale.ROOT, "%.6f", peak)

@PreviewSegueThemes
@Composable
private fun TrackDetailsContentPreview() {
    SeguePreview {
        TrackDetailsContent(
            details = TrackDetails(
                format = "FLAC",
                bitRateKbps = 1_012,
                samplingRateHz = 44_100,
                bitDepth = 16,
                channelCount = 2,
                sizeBytes = 32_400_000,
                replayGain = ReplayGain(trackGain = -7.12f, trackPeak = 0.98f),
                path = "Radiohead/In Rainbows/04 - Nude.flac",
            ),
        )
    }
}
