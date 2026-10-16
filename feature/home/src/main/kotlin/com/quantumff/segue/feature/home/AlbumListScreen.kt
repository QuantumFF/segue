package com.quantumff.segue.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.designsystem.component.AlbumCard
import com.quantumff.segue.core.designsystem.component.AlbumCardSkeleton
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.SkeletonPulse
import com.quantumff.segue.core.designsystem.component.StatusChip
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.component.rememberDelayedVisibility
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toCardState
import com.quantumff.segue.core.designsystem.model.toMenuHeader
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActions
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumListScreen(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val menuHost = remember(onArtistClick, onAddToPlaylist) {
        MediaActionHost(onArtistClick = onArtistClick, onAddToPlaylist = onAddToPlaylist)
    }
    AlbumListScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onAlbumClick = onAlbumClick,
        onAlbumAction = { album, action -> viewModel.onAlbumAction(album, action, menuHost) },
        onRefresh = viewModel::refresh,
        onMessageDismiss = viewModel::messageShown,
        modifier = modifier,
    )
}

/** A Home section's "See all": the albums in a `GridCells.Adaptive(150)` grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(
    uiState: AlbumListUiState,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onAlbumAction: (Album, MediaAction) -> Unit,
    onRefresh: () -> Unit,
    onMessageDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuAlbum by remember { mutableStateOf<Album?>(null) }
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
            SubScreenTopBar(title = stringResource(uiState.type.title), onBackClick = onBackClick)
            if (uiState.isOffline && uiState.albums.isNotEmpty()) {
                StatusChip(
                    label = stringResource(com.quantumff.segue.core.designsystem.R.string.ds_offline_cached),
                    onClick = onRefresh,
                    modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.xs),
                )
            }
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                when {
                    uiState.albums.isNotEmpty() -> AlbumGrid(
                        albums = uiState.albums,
                        onAlbumClick = { onAlbumClick(it.id) },
                        onAlbumLongClick = { menuAlbum = it },
                    )

                    uiState.isLoading || uiState.isRefreshing -> AlbumGridSkeleton()

                    else -> EmptyState(
                        icon = SegueIcons.Disc3,
                        title = stringResource(R.string.feature_home_empty_title),
                        message = null,
                        actionLabel = stringResource(R.string.feature_home_refresh),
                        onActionClick = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        SegueSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    menuAlbum?.let { album ->
        MediaContextMenu(
            header = album.toMenuHeader(),
            actions = MediaActions.album(isStarred = album.isStarred, hasArtist = album.artistId != null),
            onAction = { onAlbumAction(album, it) },
            onDismissRequest = { menuAlbum = null },
        )
    }
}

@Composable
private fun AlbumGrid(albums: ImmutableList<Album>, onAlbumClick: (Album) -> Unit, onAlbumLongClick: (Album) -> Unit) {
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(SegueSizes.albumGridCell),
        contentPadding = PaddingValues(
            start = SegueSpacing.lg,
            end = SegueSpacing.lg,
            top = SegueSpacing.sm,
            bottom = bottom + SegueSpacing.lg,
        ),
        horizontalArrangement = Arrangement.spacedBy(SegueSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }, contentType = { "albumCard" }) { album ->
            AlbumCard(
                state = remember(album) { album.toCardState() },
                onClick = { onAlbumClick(album) },
                onLongClick = { onAlbumLongClick(album) },
                artDisplaySize = SegueSizes.albumGridCell,
            )
        }
    }
}

@Composable
private fun AlbumGridSkeleton() {
    if (!rememberDelayedVisibility()) return
    SkeletonPulse {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(SegueSizes.albumGridCell),
            contentPadding = PaddingValues(SegueSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg),
            userScrollEnabled = false,
        ) {
            items(SKELETON_COUNT, contentType = { "skeleton" }) { AlbumCardSkeleton(width = SegueSizes.albumGridCell) }
        }
    }
}

private const val SKELETON_COUNT = 6

private val AlbumListType.title: Int
    get() = when (this) {
        AlbumListType.Recent -> R.string.feature_home_album_list_title_recent

        AlbumListType.Newest -> R.string.feature_home_album_list_title_newest

        AlbumListType.Frequent -> R.string.feature_home_album_list_title_frequent

        AlbumListType.Random -> R.string.feature_home_album_list_title_random

        AlbumListType.Starred -> R.string.feature_home_album_list_title_starred

        AlbumListType.Highest -> R.string.feature_home_album_list_title_highest

        AlbumListType.AlphabeticalByName, AlbumListType.AlphabeticalByArtist ->
            R.string.feature_home_album_list_title_alphabetical
    }

@PreviewSegueThemes
@Composable
private fun AlbumListScreenPreview() {
    SeguePreview {
        AlbumListScreen(
            uiState = AlbumListUiState(type = AlbumListType.Newest, albums = PreviewAlbums, isLoading = false),
            onBackClick = {},
            onAlbumClick = {},
            onAlbumAction = { _, _ -> },
            onRefresh = {},
            onMessageDismiss = {},
        )
    }
}
