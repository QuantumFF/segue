package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.designsystem.theme.tabularNumbers
import com.quantumff.segue.core.model.DownloadStatus
import kotlin.math.roundToInt

/**
 * The Download action on album, playlist and artist pages (DESIGN.md §4.3). [status] null means
 * not downloaded (download icon); queued shows the outline arrow, downloading a progress ring with
 * the percentage, downloaded a check in `success`, failed the icon in `error`.
 */
@Composable
fun DownloadButton(status: DownloadStatus?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(
        when (status) {
            null -> R.string.ds_action_download
            DownloadStatus.Queued -> R.string.ds_download_queued
            is DownloadStatus.Downloading -> R.string.ds_downloading
            DownloadStatus.Paused -> R.string.ds_download_paused
            DownloadStatus.Failed -> R.string.ds_download_failed
            DownloadStatus.Downloaded -> R.string.ds_downloaded
        },
    )
    IconButton(onClick = onClick, modifier = modifier.size(SegueSizes.touchTarget)) {
        when (status) {
            is DownloadStatus.Downloading -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            ) {
                CircularProgressIndicator(
                    progress = { status.progress },
                    strokeWidth = SegueSizes.spinnerStroke,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    gapSize = SegueSpacing.none,
                    modifier = Modifier.size(SegueSizes.icon + SegueSpacing.sm),
                )
                Text(
                    text = "${(status.progress * PERCENT).roundToInt()}",
                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            else -> Icon(
                imageVector = when (status) {
                    DownloadStatus.Queued -> SegueIcons.DownloadQueued
                    DownloadStatus.Paused -> SegueIcons.Pause
                    DownloadStatus.Downloaded -> SegueIcons.DownloadDone
                    else -> SegueIcons.Download
                },
                contentDescription = description,
                tint = when (status) {
                    DownloadStatus.Downloaded -> SegueTheme.colors.success
                    DownloadStatus.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private const val PERCENT = 100

@PreviewSegueThemes
@Composable
private fun DownloadButtonPreview() {
    SeguePreview {
        Row {
            DownloadButton(status = null, onClick = {})
            DownloadButton(status = DownloadStatus.Queued, onClick = {})
            DownloadButton(status = DownloadStatus.Downloading(0.42f), onClick = {})
            DownloadButton(status = DownloadStatus.Downloaded, onClick = {})
            DownloadButton(status = DownloadStatus.Failed, onClick = {})
        }
    }
}
