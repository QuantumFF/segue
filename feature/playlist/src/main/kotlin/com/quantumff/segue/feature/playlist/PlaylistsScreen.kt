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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.component.AccountAction
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.ErrorState
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.MediaRow
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.SkeletonPulse
import com.quantumff.segue.core.designsystem.component.TopLevelTopBar
import com.quantumff.segue.core.designsystem.component.TrackRowSkeleton
import com.quantumff.segue.core.designsystem.component.rememberDelayedVisibility
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toBadgeState
import com.quantumff.segue.core.designsystem.model.toMenuHeader
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActions
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.ScreenState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf

/** The Playlists tab (DESIGN.md §3): the playlist list with create, rename and delete. */
@Composable
fun PlaylistsScreen(
    onAccountClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlaylistsScreenContent(
        uiState = uiState,
        onAccountClick = onAccountClick,
        onPlaylistClick = onPlaylistClick,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onMenuAction = viewModel::onPlaylistAction,
        onMessageDismiss = viewModel::onMessageDismiss,
        modifier = modifier,
    )
}

/** Stateless Playlists screen: its top bar over [PlaylistsBody]. */
@Composable
fun PlaylistsScreenContent(
    uiState: PlaylistsUiState,
    onAccountClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Playlist, String) -> Unit,
    onDelete: (Playlist) -> Unit,
    onMenuAction: (Playlist, MediaAction) -> Unit,
    onMessageDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        TopLevelTopBar(title = stringResource(R.string.feature_playlist_list_title)) {
            SegueIconButton(
                icon = SegueIcons.Plus,
                contentDescription = stringResource(R.string.feature_playlist_create),
                onClick = { creating = true },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AccountAction(serverName = uiState.serverName, onClick = onAccountClick)
        }
        PlaylistsBody(
            uiState = uiState,
            creating = creating,
            onCreatingChange = { creating = it },
            onPlaylistClick = onPlaylistClick,
            onRefresh = onRefresh,
            onRetry = onRetry,
            onCreate = onCreate,
            onRename = onRename,
            onDelete = onDelete,
            onMenuAction = onMenuAction,
            onMessageDismiss = onMessageDismiss,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Stateful Playlists body without a top bar, for the Library's Playlists tab. [creating] opens the
 * create dialog, so the Library top bar's "+" can open it; see [PlaylistsBody].
 */
@Composable
fun PlaylistsTab(
    onPlaylistClick: (String) -> Unit,
    creating: Boolean,
    onCreatingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlaylistsBody(
        uiState = uiState,
        creating = creating,
        onCreatingChange = onCreatingChange,
        onPlaylistClick = onPlaylistClick,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onMenuAction = viewModel::onPlaylistAction,
        onMessageDismiss = viewModel::onMessageDismiss,
        modifier = modifier,
    )
}

/**
 * The playlists under a top bar: the list or its state (skeletons while loading, an error with
 * [onRetry], empty, or offline-empty), the row menu, the dialogs and the snackbar. Shared by the Playlists screen and the Library's Playlists tab. The caller holds
 * [creating], whether the create dialog is open, so its top bar's "+" can open it. Rename and
 * Delete menu actions open dialogs here; the rest go to [onMenuAction].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsBody(
    uiState: PlaylistsUiState,
    creating: Boolean,
    onCreatingChange: (Boolean) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
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
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (uiState.screenState) {
                ScreenState.Loading -> PlaylistsSkeleton()

                is ScreenState.Error -> ScrollableState {
                    ErrorState(
                        title = stringResource(R.string.feature_playlist_list_error_title),
                        message = stringResource(R.string.feature_playlist_list_error_message),
                        onRetryClick = onRetry,
                    )
                }

                ScreenState.Empty -> ScrollableState {
                    if (uiState.isOffline) {
                        EmptyState(
                            icon = SegueIcons.Offline,
                            title = stringResource(R.string.feature_playlist_offline_empty_title),
                            message = stringResource(R.string.feature_playlist_offline_empty_message),
                        )
                    } else {
                        EmptyState(
                            icon = SegueIcons.Playlists,
                            title = stringResource(R.string.feature_playlist_empty_title),
                            message = stringResource(R.string.feature_playlist_empty_message),
                            actionLabel = stringResource(R.string.feature_playlist_create),
                            onActionClick = { onCreatingChange(true) },
                        )
                    }
                }

                ScreenState.Content -> PlaylistList(
                    playlists = uiState.playlists,
                    downloads = uiState.downloads,
                    onPlaylistClick = onPlaylistClick,
                    onMoreClick = { menuFor = it },
                )
            }
        }
        SegueSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    menuFor?.let { playlist ->
        MediaContextMenu(
            header = playlist.toMenuHeader(),
            actions = MediaActions.playlist(
                editable = !playlist.readonly,
                isDownloaded =
                    playlist.id in uiState.downloads,
            ),
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
        dialog = dialog ?: PlaylistDialog.Create.takeIf { creating },
        onCreate = onCreate,
        onRename = onRename,
        onDelete = onDelete,
        onDismissRequest = { if (dialog != null) dialog = null else onCreatingChange(false) },
    )
}

/** Row skeletons, shown only once loading has taken longer than 150ms. */
@Composable
private fun PlaylistsSkeleton() {
    if (!rememberDelayedVisibility()) return
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    SkeletonPulse {
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottom),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(SKELETON_ROWS, contentType = { "skeleton" }) { TrackRowSkeleton() }
        }
    }
}

/** A full-size state that still scrolls, so pull-to-refresh works on it. */
@Composable
private fun ScrollableState(content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "state", contentType = "state") {
            Box(Modifier.fillParentMaxSize()) { content() }
        }
    }
}

private const val SKELETON_ROWS = 10

@Composable
private fun PlaylistList(
    playlists: ImmutableList<Playlist>,
    downloads: ImmutableMap<String, DownloadStatus>,
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
                download = downloads[playlist.id]?.toBadgeState(),
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
            onRetry = {},
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
            onRetry = {},
            onCreate = {},
            onRename = { _, _ -> },
            onDelete = {},
            onMenuAction = { _, _ -> },
            onMessageDismiss = {},
        )
    }
}
