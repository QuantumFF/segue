package com.quantumff.segue.feature.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueBaseColors
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing

/** What the queue header can ask for: the server queue, and the ⋮ menu's Save and Clear. */
@Immutable
data class QueueHeaderActions(
    val onUpload: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onConfirmDownload: () -> Unit = {},
    val onCancelDownload: () -> Unit = {},
    val onSaveAsPlaylist: (String) -> Unit = {},
    val onClear: () -> Unit = {},
)

internal fun QueueViewModel.headerActions() = QueueHeaderActions(
    onUpload = ::uploadQueue,
    onDownload = ::downloadQueue,
    onConfirmDownload = ::confirmDownload,
    onCancelDownload = ::cancelDownload,
    onSaveAsPlaylist = ::saveAsPlaylist,
    onClear = ::clearQueue,
)

/**
 * The row above the queue (DESIGN.md §4.8): the track count, then jump to the current track
 * (only while it is off-screen), upload, download, and ⋮ (Save as playlist, Clear queue). Its
 * menu and dialogs draw in the app's own colors, as the queue may sit on Now Playing's art.
 */
@Composable
internal fun QueueHeader(
    state: QueueUiState,
    actions: QueueHeaderActions,
    showJumpToCurrent: Boolean,
    onJumpToCurrent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf(QueueDialog.None) }
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
        SegueIconButton(
            icon = SegueIcons.UploadQueue,
            contentDescription = stringResource(R.string.feature_queue_upload),
            onClick = actions.onUpload,
            enabled = state.trackCount > 0 && !state.isTransferring,
        )
        SegueIconButton(
            icon = SegueIcons.DownloadQueue,
            contentDescription = stringResource(R.string.feature_queue_download),
            onClick = actions.onDownload,
            enabled = !state.isTransferring,
        )
        QueueMenu(
            enabled = state.trackCount > 0,
            onSaveClick = { dialog = QueueDialog.Save },
            onClearClick = { dialog = QueueDialog.Clear },
        )
    }
    SegueBaseColors {
        when (dialog) {
            QueueDialog.None -> Unit

            QueueDialog.Clear -> ClearQueueDialog(
                trackCount = state.trackCount,
                onConfirm = {
                    dialog = QueueDialog.None
                    actions.onClear()
                },
                onDismiss = { dialog = QueueDialog.None },
            )

            QueueDialog.Save -> SaveAsPlaylistDialog(
                onConfirm = { name ->
                    dialog = QueueDialog.None
                    actions.onSaveAsPlaylist(name)
                },
                onDismiss = { dialog = QueueDialog.None },
            )
        }
        state.downloadConfirmation?.let {
            DownloadQueueDialog(it, onConfirm = actions.onConfirmDownload, onDismiss = actions.onCancelDownload)
        }
    }
}

private enum class QueueDialog {
    None,
    Clear,
    Save,
}

@Composable
private fun QueueMenu(enabled: Boolean, onSaveClick: () -> Unit, onClearClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SegueIconButton(
            icon = SegueIcons.MoreVertical,
            contentDescription = stringResource(com.quantumff.segue.core.designsystem.R.string.ds_more_options),
            onClick = { expanded = true },
            enabled = enabled,
        )
        SegueBaseColors {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = SegueShapes.lg) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_queue_save_as_playlist)) },
                    leadingIcon = { Icon(SegueIcons.AddToPlaylist, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSaveClick()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.feature_queue_clear), color = MaterialTheme.colorScheme.error)
                    },
                    leadingIcon = {
                        Icon(SegueIcons.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = {
                        expanded = false
                        onClearClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun ClearQueueDialog(trackCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SegueShapes.xl,
        title = { Text(stringResource(R.string.feature_queue_clear_title)) },
        text = { Text(pluralStringResource(R.plurals.feature_queue_clear_message, trackCount, trackCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.feature_queue_clear_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_queue_cancel)) } },
    )
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

@Composable
private fun SaveAsPlaylistDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SegueShapes.xl,
        title = { Text(stringResource(R.string.feature_queue_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.feature_queue_playlist_name)) },
                singleLine = true,
                shape = SegueShapes.sm,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.feature_queue_save_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_queue_cancel)) } },
    )
}
