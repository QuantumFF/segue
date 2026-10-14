package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.art.CoverArt
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewData
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueAlpha
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.designsystem.theme.tabularNumbers
import com.quantumff.segue.core.designsystem.util.formatDuration

/** UI state for one track row (DESIGN.md §5.2). */
@Immutable
data class TrackRowState(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Long,
    val trackNumber: Int? = null,
    val coverArtId: String? = null,
    val explicit: Boolean = false,
    val download: DownloadBadgeState? = null,
    /** The queue's current track: title and number in `primary`. */
    val isCurrent: Boolean = false,
    /** Animates the equalizer; only meaningful with [isCurrent]. */
    val isPlaying: Boolean = false,
    /** False while offline and not downloaded: 38% alpha, not clickable. */
    val isAvailable: Boolean = true,
)

/** What the leading slot shows. */
enum class TrackRowLeading {
    /** 40 art, radius `sm`. Default outside album context. */
    Art,

    /** Track number, 32 wide. Album context; the secondary line drops the album name. */
    Number,
}

/**
 * Phone track row: 56 tall (48 when [dense]), horizontal padding 16. Tap plays.
 * The tablet table layout (DESIGN.md §5.2, Expanded column) is built with the browse screens.
 */
@Composable
fun TrackRow(
    state: TrackRowState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: TrackRowLeading = TrackRowLeading.Art,
    dense: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
) {
    val titleColor = if (state.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (dense) SegueSizes.trackRowDense else SegueSizes.trackRow)
            .rowClickable(enabled = state.isAvailable, onClick = onClick, onLongClick = onLongClick)
            .then(if (state.isAvailable) Modifier else Modifier.alpha(SegueAlpha.DISABLED))
            .padding(start = SegueSpacing.lg, end = if (onMoreClick == null) SegueSpacing.lg else SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (leading) {
            TrackRowLeading.Art -> Box(contentAlignment = Alignment.Center) {
                CoverArt(
                    coverArtId = state.coverArtId,
                    displaySize = SegueSizes.trackRowArt,
                    shape = SegueShapes.sm,
                    modifier = Modifier.size(SegueSizes.trackRowArt),
                )
                if (state.isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(SegueSizes.trackRowArt)
                            .background(MaterialTheme.colorScheme.scrim, SegueShapes.sm),
                    )
                    EqualizerBars(animating = state.isPlaying, color = SegueTheme.colors.onArt)
                }
            }

            TrackRowLeading.Number -> Box(
                modifier = Modifier.width(SegueSizes.trackNumberWidth),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.isCurrent) {
                    EqualizerBars(animating = state.isPlaying)
                } else {
                    Text(
                        text = state.trackNumber?.toString().orEmpty(),
                        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                        color = muted,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
        Spacer(Modifier.width(SegueSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.explicit) {
                    ExplicitBadge()
                    Spacer(Modifier.width(SegueSpacing.xs))
                }
                Text(
                    text = if (leading == TrackRowLeading.Number || state.album == null) {
                        state.artist
                    } else {
                        "${state.artist} · ${state.album}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        state.download?.let {
            Spacer(Modifier.width(SegueSpacing.sm))
            DownloadBadge(it)
        }
        Spacer(Modifier.width(SegueSpacing.sm))
        Text(
            text = formatDuration(state.durationSeconds),
            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
            color = muted,
        )
        if (onMoreClick != null) {
            IconButton(
                onClick = onMoreClick,
                enabled = state.isAvailable,
                modifier = Modifier.size(SegueSizes.overflowButton),
            ) {
                Icon(
                    imageVector = SegueIcons.MoreVertical,
                    contentDescription = stringResource(R.string.ds_more_options),
                    tint = muted,
                    modifier = Modifier.size(SegueSizes.iconSmall),
                )
            }
        }
    }
}

@PreviewSegueThemes
@Composable
private fun TrackRowPreview() {
    SeguePreview {
        Column {
            PreviewData.tracks.forEach { TrackRow(state = it, onClick = {}, onMoreClick = {}) }
            PreviewData.tracks.forEach {
                TrackRow(state = it, onClick = {}, leading = TrackRowLeading.Number, onMoreClick = {})
            }
        }
    }
}
