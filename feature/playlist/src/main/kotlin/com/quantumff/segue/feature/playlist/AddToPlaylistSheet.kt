package com.quantumff.segue.feature.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.component.InlineSpinner
import com.quantumff.segue.core.designsystem.component.MediaRow
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.Playlist
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * "Add to playlist" (DESIGN.md §5.7): a bottom sheet listing the user's playlists plus "New
 * playlist". Picking one adds [songIds] and closes the sheet. Shown by the app whenever a screen
 * asks to add songs to a playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    songIds: ImmutableList<String>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) currentOnDismissRequest()
    }
    DisposableEffect(viewModel) { onDispose(viewModel::reset) }
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SegueShapes.sheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = SegueSizes.noElevation,
        modifier = modifier,
    ) {
        AddToPlaylistSheetContent(
            uiState = uiState,
            onPlaylistClick = { viewModel.addTo(it, songIds) },
            onNewPlaylistClick = { creating = true },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
    if (creating) {
        PlaylistNameDialog(
            title = stringResource(R.string.feature_playlist_create),
            confirmLabel = stringResource(R.string.feature_playlist_create_action),
            initialName = "",
            onConfirm = { name ->
                creating = false
                viewModel.createWith(name, songIds)
            },
            onDismissRequest = { creating = false },
        )
    }
}

/** The sheet body: header, "New playlist", then the playlists. Rows are disabled while saving. */
@Composable
fun AddToPlaylistSheetContent(
    uiState: AddToPlaylistUiState,
    onPlaylistClick: (Playlist) -> Unit,
    onNewPlaylistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !uiState.isSaving
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SegueSizes.sheetRow)
                .padding(horizontal = SegueSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_playlist_add_to_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (uiState.isSaving) InlineSpinner()
        }
        if (uiState.failed) {
            Text(
                text = stringResource(R.string.feature_playlist_error_generic),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.xs),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.heightIn(max = SHEET_LIST_MAX_HEIGHT)) {
            item(key = "new", contentType = "new") {
                NewPlaylistRow(enabled = enabled, onClick = onNewPlaylistClick)
            }
            items(uiState.playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                MediaRow(
                    title = playlist.name,
                    subtitle = playlistSummary(playlist.songCount, playlist.durationSeconds),
                    coverArtId = playlist.coverArtId,
                    onClick = { if (enabled) onPlaylistClick(playlist) },
                    placeholderIcon = SegueIcons.Playlists,
                )
            }
        }
        Spacer(Modifier.height(SegueSpacing.sm))
    }
}

@Composable
private fun NewPlaylistRow(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SegueSizes.mediaRow)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = SegueSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(SegueSizes.mediaRowArt), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = SegueIcons.Plus,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(SegueSizes.icon),
            )
        }
        Spacer(Modifier.width(SegueSpacing.md))
        Text(
            text = stringResource(R.string.feature_playlist_create),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Keeps the sheet below full height so its drag handle and scrim stay reachable. */
private val SHEET_LIST_MAX_HEIGHT = 480.dp

@PreviewSegueThemes
@Composable
private fun AddToPlaylistSheetContentPreview() {
    SeguePreview {
        AddToPlaylistSheetContent(
            uiState = AddToPlaylistUiState(
                playlists = persistentListOf(
                    Playlist(
                        "1", "Road trip", null, "demo", true, 42, 9_120, null, null, null, readonly = false,
                    ),
                ),
                failed = true,
            ),
            onPlaylistClick = {},
            onNewPlaylistClick = {},
        )
    }
}
