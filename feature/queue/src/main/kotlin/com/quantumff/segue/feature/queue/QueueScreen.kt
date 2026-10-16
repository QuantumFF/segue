package com.quantumff.segue.feature.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import kotlinx.coroutines.launch

/** The full-screen queue route (DESIGN.md §4.8). */
@Composable
fun QueueRoute(onBackClick: () -> Unit, modifier: Modifier = Modifier, viewModel: QueueViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val actions = remember(viewModel) { viewModel.actions() }
    QueueMessages(state.message, snackbarHostState, viewModel::undoRemove, viewModel::onMessageShown)
    QueueScreen(
        state = state,
        actions = actions,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onClearQueue = viewModel::clearQueue,
        onSaveAsPlaylist = viewModel::saveAsPlaylist,
        modifier = modifier,
        transfer = remember(viewModel) {
            QueueTransferActions(
                onUpload = viewModel::uploadQueue,
                onDownload = viewModel::downloadQueue,
                onConfirmDownload = viewModel::confirmDownload,
                onCancelDownload = viewModel::cancelDownload,
            )
        },
    )
}

/** The top bar's "Upload queue" and "Download queue" buttons, and the download's confirmation. */
@Immutable
data class QueueTransferActions(
    val onUpload: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onConfirmDownload: () -> Unit = {},
    val onCancelDownload: () -> Unit = {},
)

@Composable
fun QueueScreen(
    state: QueueUiState,
    actions: QueueActions,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onClearQueue: () -> Unit,
    onSaveAsPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    transfer: QueueTransferActions = QueueTransferActions(),
    listState: LazyListState = rememberLazyListState(),
) {
    var dialog by rememberSaveable { mutableStateOf(QueueDialog.None) }
    val scope = rememberCoroutineScope()
    val currentVisible by rememberCurrentTrackVisible(listState, state.currentKey)
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SubScreenTopBar(
                title = stringResource(R.string.feature_queue_title),
                subtitle = state.trackCount.takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.feature_queue_subtitle, it, it) },
                onBackClick = onBackClick,
            ) {
                if (!currentVisible) {
                    SegueIconButton(
                        icon = SegueIcons.ScrollToCurrent,
                        contentDescription = stringResource(R.string.feature_queue_scroll_to_current),
                        onClick = {
                            val key = state.currentKey ?: return@SegueIconButton
                            val index = queueIndexOf(state.items, key, hasBanner = state.remoteOffer != null)
                            if (index >= 0) scope.launch { listState.animateScrollToItem((index - 1).coerceAtLeast(0)) }
                        },
                    )
                }
                SegueIconButton(
                    icon = SegueIcons.UploadQueue,
                    contentDescription = stringResource(R.string.feature_queue_upload),
                    onClick = transfer.onUpload,
                    enabled = state.trackCount > 0 && !state.isTransferring,
                )
                SegueIconButton(
                    icon = SegueIcons.DownloadQueue,
                    contentDescription = stringResource(R.string.feature_queue_download),
                    onClick = transfer.onDownload,
                    enabled = !state.isTransferring,
                )
                QueueMenu(
                    enabled = state.trackCount > 0,
                    onSaveClick = { dialog = QueueDialog.Save },
                    onClearClick = { dialog = QueueDialog.Clear },
                )
            }
            QueueContent(
                state = state,
                actions = actions,
                listState = listState,
                contentPadding = WindowInsets.navigationBars.asPaddingValues().let {
                    PaddingValues(bottom = it.calculateBottomPadding() + SegueSpacing.lg)
                },
            )
        }
        SegueSnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
    when (dialog) {
        QueueDialog.None -> Unit

        QueueDialog.Clear -> ClearQueueDialog(
            trackCount = state.trackCount,
            onConfirm = {
                dialog = QueueDialog.None
                onClearQueue()
            },
            onDismiss = { dialog = QueueDialog.None },
        )

        QueueDialog.Save -> SaveAsPlaylistDialog(
            onConfirm = { name ->
                dialog = QueueDialog.None
                onSaveAsPlaylist(name)
            },
            onDismiss = { dialog = QueueDialog.None },
        )
    }
    state.downloadConfirmation?.let {
        DownloadQueueDialog(it, onConfirm = transfer.onConfirmDownload, onDismiss = transfer.onCancelDownload)
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
                text = { Text(stringResource(R.string.feature_queue_clear), color = MaterialTheme.colorScheme.error) },
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

@PreviewSegueThemes
@Composable
private fun QueueScreenPreview() {
    SeguePreview {
        QueueScreen(
            state = PreviewQueue,
            actions = QueueActions(),
            snackbarHostState = remember { SnackbarHostState() },
            onBackClick = {},
            onClearQueue = {},
            onSaveAsPlaylist = {},
        )
    }
}
