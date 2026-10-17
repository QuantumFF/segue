package com.quantumff.segue.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.designsystem.component.AccountAction
import com.quantumff.segue.core.designsystem.component.AlbumCard
import com.quantumff.segue.core.designsystem.component.AlbumCarouselSection
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.HorizontalCarousel
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.Pill
import com.quantumff.segue.core.designsystem.component.SectionHeader
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.StatusBarScrim
import com.quantumff.segue.core.designsystem.component.StatusChip
import com.quantumff.segue.core.designsystem.component.SyncStatusBar
import com.quantumff.segue.core.designsystem.component.TopLevelTopBar
import com.quantumff.segue.core.designsystem.component.text
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toCardState
import com.quantumff.segue.core.designsystem.model.toMenuHeader
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActions
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.SyncPhase
import com.quantumff.segue.core.model.SyncProgress
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun HomeScreen(
    onAccountClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onSeeAllClick: (AlbumListType) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val menuHost = remember(onArtistClick, onAddToPlaylist) {
        MediaActionHost(onArtistClick = onArtistClick, onAddToPlaylist = onAddToPlaylist)
    }
    HomeScreen(
        uiState = uiState,
        onAccountClick = onAccountClick,
        onAlbumClick = onAlbumClick,
        onPlaylistClick = onPlaylistClick,
        onGenreClick = onGenreClick,
        onSeeAllClick = onSeeAllClick,
        onPlayAlbum = viewModel::playAlbum,
        onAlbumAction = { album, action -> viewModel.onAlbumAction(album, action, menuHost) },
        onRefresh = viewModel::refresh,
        onRefreshExplore = viewModel::refreshExplore,
        onRetrySync = viewModel::retrySync,
        onMessageDismiss = viewModel::messageShown,
        modifier = modifier,
    )
}

/** Home (DESIGN.md §4.1): feature carousel, then album sections, playlists and genre pills. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAccountClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onSeeAllClick: (AlbumListType) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    onAlbumAction: (Album, MediaAction) -> Unit,
    onRefresh: () -> Unit,
    onRefreshExplore: () -> Unit,
    onRetrySync: () -> Unit,
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
    val sections = AlbumSectionCallbacks(
        onAlbumClick = { onAlbumClick(it.id) },
        onAlbumLongClick = { menuAlbum = it },
        onSeeAllClick = onSeeAllClick,
    )

    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = LocalShellContentPadding.current.calculateBottomPadding()),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "topBar", contentType = "topBar") {
                    TopLevelTopBar(title = stringResource(R.string.feature_home_title)) {
                        AccountAction(serverName = uiState.serverName, onClick = onAccountClick)
                    }
                }
                item(key = "sync", contentType = "sync") {
                    SyncStatusBar(progress = uiState.syncProgress, onRetryClick = onRetrySync)
                }
                if (uiState.isOffline && !uiState.isEmpty) {
                    item(key = "offline", contentType = "offline") {
                        StatusChip(
                            label = stringResource(
                                com.quantumff.segue.core.designsystem.R.string.ds_offline_cached,
                            ),
                            onClick = onRefresh,
                            modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.xs),
                        )
                    }
                }
                if (uiState.isEmpty && !uiState.isLoading) {
                    item(key = "empty", contentType = "empty") { HomeEmptyState(uiState.isSyncing, onRefresh) }
                }
                homeSections(uiState, sections, onPlayAlbum, onPlaylistClick, onGenreClick, onRefreshExplore)
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
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

/** Click handling shared by every album section. */
private class AlbumSectionCallbacks(
    val onAlbumClick: (Album) -> Unit,
    val onAlbumLongClick: (Album) -> Unit,
    val onSeeAllClick: (AlbumListType) -> Unit,
)

private fun LazyListScope.homeSections(
    uiState: HomeUiState,
    callbacks: AlbumSectionCallbacks,
    onPlayAlbum: (Album) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onRefreshExplore: () -> Unit,
) {
    if (uiState.featured.isNotEmpty()) {
        item(key = "featured", contentType = "featured") {
            FeaturedCarousel(
                albums = uiState.featured,
                onAlbumClick = callbacks.onAlbumClick,
                onPlayClick = onPlayAlbum,
                modifier = Modifier.padding(top = SegueSpacing.sm),
            )
        }
    }
    albumSection(
        "recent",
        R.string.feature_home_recently_played,
        uiState.recentlyPlayed,
        AlbumListType.Recent,
        callbacks,
    )
    albumSection("newest", R.string.feature_home_recently_added, uiState.recentlyAdded, AlbumListType.Newest, callbacks)
    albumSection("frequent", R.string.feature_home_most_played, uiState.mostPlayed, AlbumListType.Frequent, callbacks)
    if (uiState.explore.isNotEmpty()) {
        item(key = "explore", contentType = "albumSection") {
            Column(Modifier.padding(top = SegueSpacing.sectionGap)) {
                SectionHeader(
                    title = stringResource(R.string.feature_home_explore),
                    onSeeAllClick = { callbacks.onSeeAllClick(AlbumListType.Random) },
                    extraIcon = SegueIcons.Refresh,
                    extraIconDescription = stringResource(R.string.feature_home_explore_refresh),
                    onExtraIconClick = onRefreshExplore,
                )
                AlbumRow(uiState.explore, callbacks)
            }
        }
    }
    albumSection("favorites", R.string.feature_home_favorites, uiState.favorites, AlbumListType.Starred, callbacks)
    if (uiState.playlists.isNotEmpty()) {
        item(key = "playlists", contentType = "playlistSection") {
            PlaylistSection(uiState.playlists, onPlaylistClick)
        }
    }
    if (uiState.genres.isNotEmpty()) {
        item(key = "genres", contentType = "genreSection") {
            GenreSection(uiState.genres, onGenreClick)
        }
    }
    item(key = "bottomGap", contentType = "spacer") { Spacer(Modifier.height(SegueSpacing.sectionGap)) }
}

private fun LazyListScope.albumSection(
    key: String,
    title: Int,
    albums: ImmutableList<Album>,
    type: AlbumListType,
    callbacks: AlbumSectionCallbacks,
) {
    if (albums.isEmpty()) return
    item(key = key, contentType = "albumSection") {
        AlbumCarouselSection(
            title = stringResource(title),
            albums = remember(albums) { albums.map { it.toCardState() }.toImmutableList() },
            onAlbumClick = { card -> albums.firstOrNull { it.id == card.id }?.let(callbacks.onAlbumClick) },
            onAlbumLongClick = { card -> albums.firstOrNull { it.id == card.id }?.let(callbacks.onAlbumLongClick) },
            onSeeAllClick = { callbacks.onSeeAllClick(type) },
            modifier = Modifier.padding(top = SegueSpacing.sectionGap),
        )
    }
}

/** The Explore row: a carousel without its own header (the header carries the refresh icon). */
@Composable
private fun AlbumRow(albums: ImmutableList<Album>, callbacks: AlbumSectionCallbacks) {
    HorizontalCarousel(
        items = albums,
        key = { it.id },
        itemWidth = SegueSizes.albumCard,
        onSeeAllClick = { callbacks.onSeeAllClick(AlbumListType.Random) },
        contentType = "albumCard",
    ) { album ->
        AlbumCard(
            state = remember(album) { album.toCardState() },
            onClick = { callbacks.onAlbumClick(album) },
            onLongClick = { callbacks.onAlbumLongClick(album) },
            artDisplaySize = SegueSizes.albumCard,
            modifier = Modifier.width(SegueSizes.albumCard),
        )
    }
}

@Composable
private fun PlaylistSection(playlists: ImmutableList<Playlist>, onPlaylistClick: (String) -> Unit) {
    Column(Modifier.padding(top = SegueSpacing.sectionGap)) {
        SectionHeader(title = stringResource(R.string.feature_home_playlists))
        HorizontalCarousel(
            items = playlists,
            key = { it.id },
            itemWidth = SegueSizes.albumCard,
            contentType = "playlistCard",
        ) { playlist ->
            val subtitle =
                pluralStringResource(R.plurals.feature_home_track_count, playlist.songCount, playlist.songCount)
            AlbumCard(
                state = playlist.toCardState(subtitle),
                onClick = { onPlaylistClick(playlist.id) },
                artDisplaySize = SegueSizes.albumCard,
                modifier = Modifier.width(SegueSizes.albumCard),
            )
        }
    }
}

@Composable
private fun GenreSection(genres: ImmutableList<Genre>, onGenreClick: (String) -> Unit) {
    Column(Modifier.padding(top = SegueSpacing.sectionGap)) {
        SectionHeader(title = stringResource(R.string.feature_home_genres))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SegueSpacing.lg),
        ) {
            genres.forEach { genre -> Pill(label = genre.name, onClick = { onGenreClick(genre.name) }) }
        }
    }
}

@Composable
private fun HomeEmptyState(syncing: Boolean, onRefresh: () -> Unit) {
    if (syncing) {
        EmptyState(
            icon = SegueIcons.Refresh,
            title = stringResource(R.string.feature_home_syncing_title),
            message = stringResource(R.string.feature_home_syncing_message),
            modifier = Modifier.padding(vertical = SegueSpacing.xxxl),
        )
    } else {
        EmptyState(
            icon = SegueIcons.Disc3,
            title = stringResource(R.string.feature_home_empty_title),
            message = stringResource(R.string.feature_home_empty_message),
            actionLabel = stringResource(R.string.feature_home_refresh),
            onActionClick = onRefresh,
            modifier = Modifier.padding(vertical = SegueSpacing.xxxl),
        )
    }
}

@PreviewSegueThemes
@Composable
private fun HomeScreenPreview() {
    SeguePreview {
        HomeScreen(
            uiState = PreviewHomeState,
            onAccountClick = {},
            onAlbumClick = {},
            onPlaylistClick = {},
            onGenreClick = {},
            onSeeAllClick = {},
            onPlayAlbum = {},
            onAlbumAction = { _, _ -> },
            onRefresh = {},
            onRefreshExplore = {},
            onRetrySync = {},
            onMessageDismiss = {},
        )
    }
}

@PreviewSegueThemes
@Composable
private fun HomeScreenEmptyPreview() {
    SeguePreview {
        HomeScreen(
            uiState = HomeUiState(isLoading = false, syncProgress = SyncProgress.Running(SyncPhase.Albums, 120, 900)),
            onAccountClick = {},
            onAlbumClick = {},
            onPlaylistClick = {},
            onGenreClick = {},
            onSeeAllClick = {},
            onPlayAlbum = {},
            onAlbumAction = { _, _ -> },
            onRefresh = {},
            onRefreshExplore = {},
            onRetrySync = {},
            onMessageDismiss = {},
        )
    }
}

internal val PreviewHomeState = HomeUiState(
    serverName = "demo.navidrome.org",
    featured = PreviewAlbums,
    recentlyPlayed = PreviewAlbums,
    recentlyAdded = PreviewAlbums.reversed().toImmutableList(),
    genres = persistentListOf(Genre("Rock", 120, 12), Genre("Electronic", 80, 9), Genre("Jazz", 40, 4)),
    isLoading = false,
)
