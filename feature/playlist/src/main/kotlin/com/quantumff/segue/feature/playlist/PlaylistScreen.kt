package com.quantumff.segue.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.art.rememberHeroTint
import com.quantumff.segue.core.designsystem.component.ContextMenuItem
import com.quantumff.segue.core.designsystem.component.ContextMenuSheet
import com.quantumff.segue.core.designsystem.component.DownloadButton
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.ErrorState
import com.quantumff.segue.core.designsystem.component.HeroDetailLayout
import com.quantumff.segue.core.designsystem.component.HeroHeaderState
import com.quantumff.segue.core.designsystem.component.InlineSpinner
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.designsystem.component.MediaActions
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.PlayButton
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.StatusChip
import com.quantumff.segue.core.designsystem.component.TrackRow
import com.quantumff.segue.core.designsystem.component.TrackRowLeading
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toBadgeState
import com.quantumff.segue.core.designsystem.model.toMenuHeader
import com.quantumff.segue.core.designsystem.model.toTrackRowState
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Playlist detail (DESIGN.md §4.5). */
@Composable
fun PlaylistScreen(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    playActions: PlayActions,
    onAddToPlaylist: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playlistId = uiState.playlist?.id
    val currentOnBackClick by rememberUpdatedState(onBackClick)
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) currentOnBackClick()
    }
    PlaylistScreenContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onPlayClick = { shuffle -> playActions.onPlaySongs(uiState.songs, 0, shuffle) },
        onShuffleAlbumsClick = { playlistId?.let { playActions.shuffleAlbums(AlbumSource.Playlist(it)) } },
        onTrackClick = { index -> playActions.onPlaySongs(uiState.songs, index, false) },
        onTrackAction = { index, song, action ->
            when (action) {
                MediaAction.PlayNext -> playActions.onPlayNext(listOf(song))
                MediaAction.AddToQueue -> playActions.onAddToQueue(listOf(song))
                MediaAction.InstantMix -> playActions.onStartInstantMix(song)
                MediaAction.AddToPlaylist -> onAddToPlaylist(listOf(song.id))
                MediaAction.Favorite, MediaAction.Unfavorite -> viewModel.toggleStar(song)
                MediaAction.GoToAlbum -> song.albumId?.let(onAlbumClick)
                MediaAction.GoToArtist -> song.artistId?.let(onArtistClick)
                MediaAction.RemoveFromPlaylist -> viewModel.removeAt(index)
                MediaAction.Download -> viewModel.downloadSong(song)
                else -> Unit
            }
        },
        onEditClick = viewModel::startEditing,
        onCancelEditClick = viewModel::cancelEditing,
        onSaveClick = viewModel::save,
        onMove = viewModel::move,
        onRemoveEntry = viewModel::removeEntry,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onRetryClick = viewModel::refresh,
        onMessageDismiss = viewModel::onMessageDismiss,
        modifier = modifier,
        onDownloadClick = viewModel::download,
        onRemoveDownloadClick = viewModel::removeDownload,
    )
}

/** Stateless playlist detail. [onPlayClick] takes `shuffle`. */
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreenContent(
    uiState: PlaylistUiState,
    onBackClick: () -> Unit,
    onPlayClick: (shuffle: Boolean) -> Unit,
    onShuffleAlbumsClick: () -> Unit,
    onTrackClick: (index: Int) -> Unit,
    onTrackAction: (index: Int, song: Song, action: MediaAction) -> Unit,
    onEditClick: () -> Unit,
    onCancelEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemoveEntry: (key: String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRetryClick: () -> Unit,
    onMessageDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDownloadClick: () -> Unit = {},
    onRemoveDownloadClick: () -> Unit = {},
) {
    val playlist = uiState.playlist
    var showPlaylistMenu by remember { mutableStateOf(false) }
    var showDownloadMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }
    var trackMenu by remember { mutableStateOf<Pair<Int, Song>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.message?.let { stringResource(it.text) }
    val currentOnMessageDismiss by rememberUpdatedState(onMessageDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            currentOnMessageDismiss()
        }
    }
    val rowHeightPx = with(LocalDensity.current) { SegueSizes.trackRow.toPx() }
    val entries = rememberUpdatedState(uiState.entries)
    val currentOnMove = rememberUpdatedState(onMove)
    val reorder = remember(rowHeightPx) { DragReorderState(rowHeightPx, entries, currentOnMove) }

    val hero = HeroHeaderState(
        overline = stringResource(R.string.feature_playlist_overline),
        title = playlist?.name.orEmpty(),
        subtitle = playlist?.comment?.takeIf { it.isNotBlank() },
        metadata = playlist?.let {
            playlistMetadata(it, uiState.entries.size, uiState.entries.sumOf { entry -> entry.song.durationSeconds })
        },
        coverArtId = playlist?.coverArtId,
        tint = rememberHeroTint(playlist?.coverArtId),
    )
    val canPlay = uiState.entries.isNotEmpty() && !uiState.isEditing

    Box(modifier.fillMaxSize()) {
        HeroDetailLayout(
            hero = hero,
            onBackClick = onBackClick,
            onPlayClick = if (canPlay) ({ onPlayClick(false) }) else null,
            onMoreClick = if (uiState.isEditable && !uiState.isEditing) ({ showPlaylistMenu = true }) else null,
            heroActions = {
                if (uiState.isEditing) {
                    OutlinedButton(onClick = onCancelEditClick, enabled = !uiState.isSaving) {
                        Text(stringResource(R.string.feature_playlist_cancel))
                    }
                    Button(onClick = onSaveClick, enabled = !uiState.isSaving) {
                        if (uiState.isSaving) InlineSpinner() else Text(stringResource(R.string.feature_playlist_save))
                    }
                } else {
                    PlayButton(onClick = { onPlayClick(false) })
                    SegueIconButton(
                        icon = SegueIcons.Shuffle,
                        contentDescription = stringResource(R.string.feature_playlist_shuffle),
                        onClick = { onPlayClick(true) },
                        enabled = canPlay,
                    )
                    SegueIconButton(
                        icon = SegueIcons.ShuffleAlbums,
                        contentDescription = stringResource(R.string.feature_playlist_shuffle_albums),
                        onClick = onShuffleAlbumsClick,
                        enabled = canPlay,
                    )
                    DownloadButton(
                        status = uiState.download,
                        onClick = { if (uiState.download == null) onDownloadClick() else showDownloadMenu = true },
                    )
                    if (uiState.isEditable) {
                        SegueIconButton(
                            icon = SegueIcons.MoreVertical,
                            contentDescription = stringResource(R.string.feature_playlist_more),
                            onClick = { showPlaylistMenu = true },
                        )
                    }
                }
            },
        ) {
            if (uiState.loadState == LoadState.Failed && playlist != null) {
                item(key = "offline", contentType = "offline") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SegueSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        StatusChip(label = stringResource(R.string.feature_playlist_offline), onClick = onRetryClick)
                    }
                }
            }
            when {
                playlist == null -> item(key = "status", contentType = "status") {
                    PlaylistStatus(uiState.loadState, onRetryClick)
                }

                uiState.entries.isEmpty() -> item(key = "empty", contentType = "status") {
                    Text(
                        text = stringResource(R.string.feature_playlist_no_tracks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(SegueSpacing.lg),
                    )
                }

                uiState.isEditing -> itemsIndexed(
                    items = uiState.entries,
                    key = { _, entry -> entry.key },
                    contentType = { _, _ -> "editableTrack" },
                ) { index, entry ->
                    EditableTrackRow(
                        entry = entry,
                        index = index,
                        reorder = reorder,
                        onRemove = { onRemoveEntry(entry.key) },
                    )
                }

                else -> itemsIndexed(
                    items = uiState.entries,
                    key = { _, entry -> entry.key },
                    contentType = { _, _ -> "track" },
                ) { index, entry ->
                    val download = uiState.availability.downloads[entry.song.id]
                    val available = uiState.availability.isAvailable(entry.song)
                    TrackRow(
                        state = remember(entry, download, available) {
                            entry.song.toTrackRowState(
                                rowKey = entry.key,
                                download = download?.status?.toBadgeState(),
                                isAvailable = available,
                            )
                        },
                        onClick = { onTrackClick(index) },
                        leading = TrackRowLeading.Art,
                        onLongClick = { trackMenu = index to entry.song },
                        onMoreClick = { trackMenu = index to entry.song },
                    )
                }
            }
        }
        SegueSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    if (showPlaylistMenu && playlist != null) {
        PlaylistMenu(
            playlist = playlist,
            onEditClick = onEditClick,
            onRenameClick = { dialog = PlaylistDialog.Rename(playlist) },
            onDeleteClick = { dialog = PlaylistDialog.Delete(playlist) },
            onDismissRequest = { showPlaylistMenu = false },
        )
    }
    if (showDownloadMenu && playlist != null) {
        MediaContextMenu(
            header = playlist.toMenuHeader(),
            actions = persistentListOf(MediaAction.RemoveDownload),
            onAction = { onRemoveDownloadClick() },
            onDismissRequest = { showDownloadMenu = false },
        )
    }
    trackMenu?.let { (index, song) ->
        MediaContextMenu(
            header = song.toMenuHeader(),
            actions = MediaActions.song(
                isStarred = song.isStarred,
                hasAlbum = song.albumId != null,
                hasArtist = song.artistId != null,
                inPlaylist = uiState.isEditable,
            ),
            onAction = { action -> onTrackAction(index, song, action) },
            onDismissRequest = { trackMenu = null },
        )
    }
    PlaylistDialogHost(
        dialog = dialog,
        onCreate = {},
        onRename = { _, name -> onRename(name) },
        onDelete = { onDelete() },
        onDismissRequest = { dialog = null },
    )
}

@Composable
private fun PlaylistStatus(loadState: LoadState, onRetryClick: () -> Unit) {
    when (loadState) {
        LoadState.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SegueSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            InlineSpinner()
        }

        LoadState.Failed -> ErrorState(
            title = stringResource(R.string.feature_playlist_load_failed),
            message = null,
            onRetryClick = onRetryClick,
        )

        LoadState.Loaded -> EmptyState(
            icon = SegueIcons.Playlists,
            title = stringResource(R.string.feature_playlist_not_found),
            message = null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistMenu(
    playlist: Playlist,
    onEditClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val edit = stringResource(R.string.feature_playlist_edit)
    val rename = stringResource(R.string.feature_playlist_rename)
    val delete = stringResource(R.string.feature_playlist_delete)
    val items = remember(edit, rename, delete) {
        persistentListOf(
            ContextMenuItem(id = MENU_EDIT, label = edit, icon = SegueIcons.DragHandle),
            ContextMenuItem(id = MENU_RENAME, label = rename, icon = SegueIcons.Edit),
            ContextMenuItem(id = MENU_DELETE, label = delete, icon = SegueIcons.Remove, destructive = true),
        )
    }
    ContextMenuSheet(
        header = playlist.toMenuHeader(),
        items = items,
        onItemClick = { item ->
            when (item.id) {
                MENU_EDIT -> onEditClick()
                MENU_RENAME -> onRenameClick()
                MENU_DELETE -> onDeleteClick()
            }
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest,
    )
}

/**
 * Drag-to-reorder for fixed-height rows, without a library: the dragged row follows the finger
 * (translation only), and each time it passes half a row it swaps with its neighbour. The index is
 * tracked here so fast drags never act on a stale list.
 */
@Stable
private class DragReorderState(
    private val rowHeightPx: Float,
    private val entries: State<ImmutableList<PlaylistEntry>>,
    private val onMove: State<(Int, Int) -> Unit>,
) {
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    private var draggingIndex by mutableIntStateOf(-1)

    fun start(key: String) {
        draggingKey = key
        draggingIndex = entries.value.indexOfFirst { it.key == key }
        offset = 0f
    }

    fun drag(deltaY: Float) {
        if (draggingKey == null || draggingIndex < 0) return
        offset += deltaY
        val move = onMove.value
        val last = entries.value.lastIndex
        if (offset > rowHeightPx / 2 && draggingIndex < last) {
            move(draggingIndex, draggingIndex + 1)
            draggingIndex++
            offset -= rowHeightPx
        } else if (offset < -rowHeightPx / 2 && draggingIndex > 0) {
            move(draggingIndex, draggingIndex - 1)
            draggingIndex--
            offset += rowHeightPx
        }
    }

    fun end() {
        draggingKey = null
        draggingIndex = -1
        offset = 0f
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LazyItemScope.EditableTrackRow(
    entry: PlaylistEntry,
    index: Int,
    reorder: DragReorderState,
    onRemove: () -> Unit,
) {
    val dragging = reorder.draggingKey == entry.key
    val dismissState = rememberSwipeToDismissBoxState()
    val background = MaterialTheme.colorScheme.background
    val dragGestures = Modifier.pointerInput(entry.key) {
        detectDragGestures(
            onDragStart = { reorder.start(entry.key) },
            onDragEnd = reorder::end,
            onDragCancel = reorder::end,
            onDrag = { change, amount ->
                change.consume()
                reorder.drag(amount.y)
            },
        )
    }
    val longPressDrag = Modifier.pointerInput(entry.key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { reorder.start(entry.key) },
            onDragEnd = reorder::end,
            onDragCancel = reorder::end,
            onDrag = { change, amount ->
                change.consume()
                reorder.drag(amount.y)
            },
        )
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !dragging,
        onDismiss = { onRemove() },
        backgroundContent = { RemoveBackground() },
        modifier = if (dragging) {
            Modifier
                .zIndex(1f)
                .graphicsLayer { translationY = reorder.offset }
        } else {
            Modifier.animateItem()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else background)
                .then(longPressDrag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackRow(
                state = entry.song.toTrackRowState(rowKey = entry.key).copy(trackNumber = index + 1),
                onClick = {},
                leading = TrackRowLeading.Art,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(SegueSizes.touchTarget)
                    .then(dragGestures),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SegueIcons.DragHandle,
                    contentDescription = stringResource(R.string.feature_playlist_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SegueSizes.iconSmall),
                )
            }
        }
    }
}

/** Swipe-left background: `error` with a trash icon at the end. */
@Composable
private fun RemoveBackground() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = SegueSpacing.lg),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SegueIcons.Remove,
            contentDescription = stringResource(R.string.feature_playlist_remove_track),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private const val MENU_EDIT = "edit"
private const val MENU_RENAME = "rename"
private const val MENU_DELETE = "delete"

private val PreviewPlaylist =
    Playlist("1", "Road trip", "Songs for the long drive", "demo", true, 3, 720, null, null, null, readonly = false)

private val PreviewEntries = listOf(
    Song(id = "s1", title = "15 Step", albumId = "a1", album = "In Rainbows", artist = "Radiohead", artistId = "r1"),
    Song(id = "s2", title = "Nude", albumId = "a1", album = "In Rainbows", artist = "Radiohead", artistId = "r1"),
    Song(id = "s3", title = "Reckoner", albumId = "a1", album = "In Rainbows", artist = "Radiohead", artistId = "r1"),
).mapIndexed { index, song -> PlaylistEntry("$index:${song.id}", song.copy(durationSeconds = 240)) }
    .toImmutableList()

@Composable
private fun PreviewPlaylistScreen(uiState: PlaylistUiState) {
    PlaylistScreenContent(
        uiState = uiState,
        onBackClick = {},
        onPlayClick = {},
        onShuffleAlbumsClick = {},
        onTrackClick = {},
        onTrackAction = { _, _, _ -> },
        onEditClick = {},
        onCancelEditClick = {},
        onSaveClick = {},
        onMove = { _, _ -> },
        onRemoveEntry = {},
        onRename = {},
        onDelete = {},
        onRetryClick = {},
        onMessageDismiss = {},
    )
}

@PreviewSegueThemes
@Composable
private fun PlaylistScreenPreview() {
    SeguePreview {
        PreviewPlaylistScreen(
            PlaylistUiState(playlist = PreviewPlaylist, entries = PreviewEntries, loadState = LoadState.Loaded),
        )
    }
}

@PreviewSegueThemes
@Composable
private fun PlaylistScreenEditingPreview() {
    SeguePreview {
        PreviewPlaylistScreen(
            PlaylistUiState(
                playlist = PreviewPlaylist,
                entries = PreviewEntries,
                loadState = LoadState.Loaded,
                isEditing = true,
            ),
        )
    }
}
