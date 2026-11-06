package com.quantumff.segue.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtons
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtonsInset
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.OfflineStatusChip
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.component.rememberCollapseOnScrollState
import com.quantumff.segue.core.designsystem.component.text
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toMenuHeader
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActions
import com.quantumff.segue.core.model.OfflineStatus
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.ScreenState
import java.text.NumberFormat
import kotlinx.coroutines.flow.flowOf

/**
 * One genre's albums: a secondary top bar, the offline chip while Offline, the album grid with
 * download badges and a long-press menu, and the floating Play / Shuffle albums buttons.
 */
@Composable
fun GenreScreen(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    onOfflineModeSettingClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GenreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val menuHost = remember(onArtistClick, onAddToPlaylist) {
        MediaActionHost(onArtistClick = onArtistClick, onAddToPlaylist = onAddToPlaylist)
    }
    GenreContent(
        state = state,
        albums = viewModel.albums.collectAsLazyPagingItems(),
        onBackClick = onBackClick,
        onAlbumClick = onAlbumClick,
        onAlbumAction = { album, action -> viewModel.onAlbumAction(album, action, menuHost) },
        onPlayAlbums = viewModel::playAlbums,
        onRetry = viewModel::retry,
        onOfflineModeSettingClick = onOfflineModeSettingClick,
        onMessageDismiss = viewModel::messageShown,
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@Composable
internal fun GenreContent(
    state: GenreUiState,
    albums: LazyPagingItems<Album>,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onAlbumAction: (Album, MediaAction) -> Unit,
    onPlayAlbums: (AlbumOrder, QueuePlacement) -> Unit,
    onRetry: () -> Unit,
    onOfflineModeSettingClick: () -> Unit,
    onMessageDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = state.albumCount?.let {
        pluralStringResource(R.plurals.feature_library_album_count, it, NumberFormat.getIntegerInstance().format(it))
    }
    var menuAlbum by remember { mutableStateOf<Album?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it.text) }
    val currentOnMessageDismiss by rememberUpdatedState(onMessageDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            currentOnMessageDismiss()
        }
    }
    val collapse = rememberCollapseOnScrollState()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SubScreenTopBar(title = state.genre, subtitle = count, onBackClick = onBackClick)
            if (state.offlineStatus.showsChip(hasContent = (state.albumCount ?: 0) > 0)) {
                OfflineStatusChip(
                    status = state.offlineStatus,
                    onRetry = onRetry,
                    onOpenOfflineModeSetting = onOfflineModeSettingClick,
                    modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.xs),
                )
            }
            val bottom = LocalShellContentPadding.current.calculateBottomPadding()
            val gridPadding = PaddingValues(
                start = SegueSpacing.lg,
                end = SegueSpacing.lg,
                top = SegueSpacing.sm,
                bottom = bottom + FloatingAlbumPlayButtonsInset,
            )
            val screen = state.screen
            Box(Modifier.weight(1f)) {
                if (screen == ScreenState.Content) {
                    PagedAlbumGrid(
                        albums = albums,
                        onAlbumClick = { onAlbumClick(it.id) },
                        onAlbumLongClick = { _, album -> menuAlbum = album },
                        downloads = state.albumDownloads,
                        contentPadding = gridPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(collapse.connection),
                    )
                } else {
                    TabWithoutContent(
                        screen = screen,
                        skeleton = TabSkeleton.Cards,
                        contentPadding = gridPadding,
                        onRetry = onRetry,
                    ) { GenreEmpty(state.offlineStatus, PaddingValues(bottom = bottom)) }
                }
            }
        }
        FloatingAlbumPlayButtons(
            visible = (state.albumCount ?: 0) > 0,
            expanded = collapse.expanded,
            onPlay = onPlayAlbums,
        )
        SegueSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    menuAlbum?.let { album ->
        MediaContextMenu(
            header = album.toMenuHeader(),
            actions = MediaActions.album(
                isStarred = album.isStarred,
                hasArtist = album.artistId != null,
                isDownloaded = album.id in state.albumDownloads,
            ),
            onAction = { onAlbumAction(album, it) },
            onDismissRequest = { menuAlbum = null },
        )
    }
}

/** No albums to show: none in the genre, or offline none of them downloaded. */
@Composable
private fun GenreEmpty(status: OfflineStatus, contentPadding: PaddingValues) {
    ScrollableEmpty(contentPadding) {
        if (status.isShowingDownloads) {
            EmptyState(
                icon = SegueIcons.Offline,
                title = stringResource(R.string.feature_library_genre_offline_empty_title),
                message = stringResource(R.string.feature_library_genre_offline_empty_message),
            )
        } else {
            EmptyState(
                icon = SegueIcons.Disc3,
                title = stringResource(R.string.feature_library_genre_empty_title),
                message = stringResource(R.string.feature_library_genre_empty_message),
            )
        }
    }
}

@PreviewSegueThemes
@Composable
private fun GenreContentPreview() {
    SeguePreview {
        GenreContent(
            state = GenreUiState(genre = "Alternative", albumCount = 0),
            albums = remember { flowOf(PagingData.empty<Album>()) }.collectAsLazyPagingItems(),
            onBackClick = {},
            onAlbumClick = {},
            onAlbumAction = { _, _ -> },
            onPlayAlbums = { _, _ -> },
            onRetry = {},
            onOfflineModeSettingClick = {},
            onMessageDismiss = {},
        )
    }
}
