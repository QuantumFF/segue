package com.quantumff.segue.feature.playlist

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.model.Playlist

/** "12 tracks · 1 h 12 m": the playlist row subtitle (DESIGN.md §4.2). */
@Composable
internal fun playlistSummary(songCount: Int, durationSeconds: Int): String {
    val tracks = pluralStringResource(R.plurals.feature_playlist_track_count, songCount, songCount)
    if (durationSeconds <= 0) return tracks
    return "$tracks · ${playlistDuration(durationSeconds)}"
}

@Composable
internal fun playlistDuration(durationSeconds: Int): String {
    val totalMinutes = (durationSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) {
        stringResource(R.string.feature_playlist_duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.feature_playlist_duration_minutes, minutes)
    }
}

/** "owner · 12 tracks · 1 h 12 m · Public": the playlist hero metadata (DESIGN.md §4.5). */
@Composable
internal fun playlistMetadata(playlist: Playlist, songCount: Int, durationSeconds: Int): String {
    val visibility = stringResource(
        if (playlist.isPublic) R.string.feature_playlist_public else R.string.feature_playlist_private,
    )
    return listOfNotNull(playlist.owner, playlistSummary(songCount, durationSeconds), visibility).joinToString(" · ")
}

/** Name prompt for creating or renaming a playlist. Confirm stays disabled while the name is blank. */
@Composable
internal fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.feature_playlist_name_label)) },
                singleLine = true,
                shape = SegueShapes.sm,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.feature_playlist_cancel)) }
        },
        modifier = modifier,
    )
}

/** Confirms deleting [name] from the server. */
@Composable
internal fun DeletePlaylistDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.feature_playlist_delete_title)) },
        text = { Text(stringResource(R.string.feature_playlist_delete_message, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.feature_playlist_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.feature_playlist_cancel)) }
        },
        modifier = modifier,
    )
}

/** A playlist dialog that is open: which one, and for which playlist. */
internal sealed interface PlaylistDialog {
    data object Create : PlaylistDialog

    data class Rename(val playlist: Playlist) : PlaylistDialog

    data class Delete(val playlist: Playlist) : PlaylistDialog
}

/** Shows [dialog]. */
@Composable
internal fun PlaylistDialogHost(
    dialog: PlaylistDialog?,
    onCreate: (String) -> Unit,
    onRename: (Playlist, String) -> Unit,
    onDelete: (Playlist) -> Unit,
    onDismissRequest: () -> Unit,
) {
    when (dialog) {
        null -> Unit

        PlaylistDialog.Create -> PlaylistNameDialog(
            title = stringResource(R.string.feature_playlist_create),
            confirmLabel = stringResource(R.string.feature_playlist_create_action),
            initialName = "",
            onConfirm = {
                onCreate(it)
                onDismissRequest()
            },
            onDismissRequest = onDismissRequest,
        )

        is PlaylistDialog.Rename -> PlaylistNameDialog(
            title = stringResource(R.string.feature_playlist_rename_title),
            confirmLabel = stringResource(R.string.feature_playlist_rename),
            initialName = dialog.playlist.name,
            onConfirm = {
                onRename(dialog.playlist, it)
                onDismissRequest()
            },
            onDismissRequest = onDismissRequest,
        )

        is PlaylistDialog.Delete -> DeletePlaylistDialog(
            name = dialog.playlist.name,
            onConfirm = {
                onDelete(dialog.playlist)
                onDismissRequest()
            },
            onDismissRequest = onDismissRequest,
        )
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

@PreviewSegueThemes
@Composable
private fun PlaylistNameDialogPreview() {
    SeguePreview {
        PlaylistNameDialog(
            title = "Rename playlist",
            confirmLabel = "Rename",
            initialName = "Road trip",
            onConfirm = {},
            onDismissRequest = {},
        )
    }
}
