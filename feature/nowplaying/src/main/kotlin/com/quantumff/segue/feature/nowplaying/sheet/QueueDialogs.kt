package com.quantumff.segue.feature.nowplaying.sheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.feature.nowplaying.R

/** The ⋮ menu's "Clear queue": confirms, since it also clears the queue saved on the server. */
@Composable
internal fun ClearQueueDialog(trackCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SegueShapes.xl,
        title = { Text(stringResource(R.string.feature_nowplaying_clear_queue_title)) },
        text = {
            Text(pluralStringResource(R.plurals.feature_nowplaying_clear_queue_message, trackCount, trackCount))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.feature_nowplaying_clear_queue_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_nowplaying_cancel)) }
        },
    )
}

/** The ⋮ menu's "Save queue as playlist": asks for the playlist's name. */
@Composable
internal fun SaveQueueAsPlaylistDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SegueShapes.xl,
        title = { Text(stringResource(R.string.feature_nowplaying_save_queue_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.feature_nowplaying_playlist_name)) },
                singleLine = true,
                shape = SegueShapes.sm,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.feature_nowplaying_save_queue_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_nowplaying_cancel)) }
        },
    )
}
