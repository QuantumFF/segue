package com.quantumff.segue.feature.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.core.designsystem.component.InlineSpinner
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueBaseColors
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing

/** What the queue header can ask for: upload, download, and the download's confirmation. */
@Immutable
data class QueueHeaderActions(
    val onUpload: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onConfirmDownload: () -> Unit = {},
    val onCancelDownload: () -> Unit = {},
)

internal fun QueueViewModel.headerActions() = QueueHeaderActions(
    onUpload = ::uploadQueue,
    onDownload = ::downloadQueue,
    onConfirmDownload = ::confirmDownload,
    onCancelDownload = ::cancelDownload,
)

/**
 * The row above the queue (DESIGN.md §4.8): the track count, then jump to the current track
 * (only while it is off-screen), upload and download. Save as playlist and Clear queue are in
 * Now Playing's ⋮ menu. The download's dialog draws in the app's own colors, as the queue sits on
 * Now Playing's art.
 */
@Composable
internal fun QueueHeader(
    state: QueueUiState,
    actions: QueueHeaderActions,
    showJumpToCurrent: Boolean,
    onJumpToCurrent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SegueSpacing.lg, end = SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (state.trackCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.feature_queue_subtitle, state.trackCount, state.trackCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showJumpToCurrent) {
            SegueIconButton(
                icon = SegueIcons.ScrollToCurrent,
                contentDescription = stringResource(R.string.feature_queue_scroll_to_current),
                onClick = onJumpToCurrent,
            )
        }
        TransferButton(
            icon = SegueIcons.UploadQueue,
            label = stringResource(R.string.feature_queue_upload),
            onClick = actions.onUpload,
            enabled = state.trackCount > 0 && !state.isTransferring,
            isRunning = state.transfer == QueueTransfer.Upload,
        )
        TransferButton(
            icon = SegueIcons.DownloadQueue,
            label = stringResource(R.string.feature_queue_download),
            onClick = actions.onDownload,
            enabled = !state.isTransferring,
            isRunning = state.transfer == QueueTransfer.Download,
        )
    }
    state.downloadConfirmation?.let {
        SegueBaseColors {
            DownloadQueueDialog(it, onConfirm = actions.onConfirmDownload, onDismiss = actions.onCancelDownload)
        }
    }
}

/** Upload or Download: its icon gives way to an inline spinner while its own transfer runs. */
@Composable
private fun TransferButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isRunning: Boolean,
) {
    if (!isRunning) {
        SegueIconButton(icon = icon, contentDescription = label, onClick = onClick, enabled = enabled)
        return
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(SegueSizes.touchTarget)
            .semantics { contentDescription = label },
    ) {
        InlineSpinner()
    }
}

@Composable
private fun DownloadQueueDialog(confirmation: DownloadConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SegueShapes.xl,
        title = { Text(stringResource(R.string.feature_queue_download_title)) },
        text = {
            Text(
                pluralStringResource(
                    R.plurals.feature_queue_download_message,
                    confirmation.trackCount,
                    confirmation.trackCount,
                    confirmation.deviceName,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.feature_queue_download_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_queue_cancel)) } },
    )
}
