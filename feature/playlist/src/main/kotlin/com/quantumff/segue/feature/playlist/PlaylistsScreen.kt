package com.quantumff.segue.feature.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.component.AccountAction
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.designsystem.component.MediaActions
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.MediaRow
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.TopLevelTopBar
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toMenuHeader
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Playlist
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The Playlists tab (DESIGN.md §3): the playlist list with create, rename and delete. */
@Composable
fun PlaylistsScreen(
    onAccountClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    playActions: PlayActions,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlaylistsScreenContent(
        uiState = uiState,
        onAccountClick = onAccountClick,
        onPlaylistClick = onPlaylistClick,
        onRefresh = viewModel::refresh,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onMenuAction = { playlist, action ->
            when (action) {
                MediaAction.Play -> viewModel.loadSongs(playlist.id) { playActions.onPlaySongs(it, 0, false) }

                MediaAction.Shuffle -> viewModel.loadSongs(playlist.id) { playActions.onPlaySongs(it, 0, true) }

                MediaAction.ShuffleAlbums -> playActions.onShuffleAlbums(AlbumShuffleSource.Playlist(playlist.id))

                MediaAction.PlayNext -> viewModel.loadSongs(playlist.id, playActions.onPlayNext)

                MediaAction.AddToQueue -> viewModel.loadSongs(playlist.id, playActions.onAddToQueue)

                // TODO(downloads): pin the playlist for offline once downloads exist.
                else -> Unit
            }
        },
        onMessageDismiss = viewModel::onMessageDismiss,
        modifier = modifier,
    )
}

/** Stateless Playlists tab. Rename and Delete menu actions open dialogs here; the rest go to [onMenuAction]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreenContent(
    uiState: PlaylistsUiState,
    onAccountClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Playlist, String) -> Unit,
    onDelete: (Playlist) -> Unit,
    onMenuAction: (Playlist, MediaAction) -> Unit,
    onMessageDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }
    var menuFor by remember { mutableStateOf<Playlist?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.message?.let { stringResource(it.text) }
    val currentOnMessageDismiss by rememberUpdatedState(onMessageDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            currentOnMessageDismiss()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopLevelTopBar(title = stringResource(R.string.feature_playlist_list_title)) {
                SegueIconButton(
                    icon = SegueIcons.Plus,
                    contentDescription = stringResource(R.string.feature_playlist_create),
                    onClick = { dialog = PlaylistDialog.Create },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AccountAction(serverName = uiState.serverName, onClick = onAccountClick)
            }
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                when {
                    uiState.isLoading -> Unit

                    uiState.playlists.isEmpty() -> EmptyState(
                        icon = SegueIcons.Playlists,
                        title = stringResource(R.string.feature_playlist_empty_title),
                        message = stringResource(R.string.feature_playlist_empty_message),
                        actionLabel = stringResource(R.string.feature_playlist_create),
                        onActionClick = { dialog = PlaylistDialog.Create },
                    )

                    else -> PlaylistList(
                        playlists = uiState.playlists,
                        onPlaylistClick = onPlaylistClick,
                        onMoreClick = { menuFor = it },
                    )
                }
            }
        }
        SegueSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    menuFor?.let { playlist ->
        MediaContextMenu(
            header = playlist.toMenuHeader(),
            actions = MediaActions.playlist(editable = !playlist.readonly),
            onAction = { action ->
                when (action) {
                    MediaAction.Rename -> dialog = PlaylistDialog.Rename(playlist)
                    MediaAction.Delete -> dialog = PlaylistDialog.Delete(playlist)
                    else -> onMenuAction(playlist, action)
                }
            },
            onDismissRequest = { menuFor = null },
        )
    }
    PlaylistDialogHost(
        dialog = dialog,
        onCreate = onCreate,
        onRename = onRename,
        onDelete = onDelete,
        onDismissRequest = { dialog = null },
    )
}

@Composable
private fun PlaylistList(
    playlists: ImmutableList<Playlist>,
    onPlaylistClick: (String) -> Unit,
    onMoreClick: (Playlist) -> Unit,
) {
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
        items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
            MediaRow(
                title = playlist.name,
                subtitle = playlistSummary(playlist.songCount, playlist.durationSeconds),
                coverArtId = playlist.coverArtId,
                onClick = { onPlaylistClick(playlist.id) },
                onLongClick = { onMoreClick(playlist) },
                onMoreClick = { onMoreClick(playlist) },
                placeholderIcon = SegueIcons.Playlists,
            )
        }
    }
}

internal val PlaylistMessage.text: Int
    get() = when (this) {
        PlaylistMessage.Failed -> R.string.feature_playlist_error_generic
        PlaylistMessage.SaveFailed -> R.string.feature_playlist_error_save
    }

private val PreviewPlaylists = persistentListOf(
    Playlist("1", "Road trip", null, "demo", true, 42, 9_120, null, null, null, readonly = false),
    Playlist("2", "Late night", "Quiet things", "demo", false, 12, 2_880, null, null, null, readonly = false),
    Playlist("3", "Shared mix", null, "admin", true, 1, 240, null, null, null, readonly = true),
)

@PreviewSegueThemes
@Composable
private fun PlaylistsScreenPreview() {
    SeguePreview {
        PlaylistsScreenContent(
            uiState = PlaylistsUiState(playlists = PreviewPlaylists, serverName = "demo", isLoading = false),
            onAccountClick = {},
            onPlaylistClick = {},
            onRefresh = {},
            onCreate = {},
            onRename = { _, _ -> },
            onDelete = {},
            onMenuAction = { _, _ -> },
            onMessageDismiss = {},
        )
    }
}

@PreviewSegueThemes
@Composable
private fun PlaylistsScreenEmptyPreview() {
    SeguePreview {
        PlaylistsScreenContent(
            uiState = PlaylistsUiState(isLoading = false),
            onAccountClick = {},
            onPlaylistClick = {},
            onRefresh = {},
            onCreate = {},
            onRename = { _, _ -> },
            onDelete = {},
            onMenuAction = { _, _ -> },
            onMessageDismiss = {},
        )
    }
}
