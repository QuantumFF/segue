package com.quantumff.segue.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.ItemSnapshotList
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.quantumff.segue.core.designsystem.component.AccountAction
import com.quantumff.segue.core.designsystem.component.ArtistCard
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtons
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtonsInset
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.designsystem.component.MediaActions
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.MediaRow
import com.quantumff.segue.core.designsystem.component.SegueFilterChip
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.SyncStatusBar
import com.quantumff.segue.core.designsystem.component.TopLevelTopBar
import com.quantumff.segue.core.designsystem.component.TrackRow
import com.quantumff.segue.core.designsystem.component.rememberCollapseOnScrollState
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toBadgeState
import com.quantumff.segue.core.designsystem.model.toCardState
import com.quantumff.segue.core.designsystem.model.toMenuHeader
import com.quantumff.segue.core.designsystem.model.toTrackRowState
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.ExplicitStatus
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongDownload
import java.text.NumberFormat
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf

/** What the Library screen asks of its host or its ViewModel. */
internal sealed interface LibraryEvent {
    data class SelectTab(val tab: LibraryTab) : LibraryEvent

    data class SetSort(val sort: AlbumSortOrder) : LibraryEvent

    data object ToggleViewMode : LibraryEvent

    data object ToggleFavorites : LibraryEvent

    data class SetGenreFilter(val genre: String?) : LibraryEvent

    data class SetYearFilter(val year: Int?) : LibraryEvent

    data object ClearFilters : LibraryEvent

    data object Refresh : LibraryEvent

    data object ShuffleAll : LibraryEvent

    data class PlayAlbums(val order: AlbumOrder, val placement: QueuePlacement) : LibraryEvent

    data class ShuffleGenreAlbums(val genre: String) : LibraryEvent

    data object OpenAccount : LibraryEvent

    data object OpenDownloads : LibraryEvent

    data class OpenAlbum(val id: String) : LibraryEvent

    data class OpenArtist(val id: String) : LibraryEvent

    data class OpenPlaylist(val id: String) : LibraryEvent

    data class OpenGenre(val name: String) : LibraryEvent

    data class PlaySong(val song: Song) : LibraryEvent

    data class AlbumMenu(val album: Album, val action: MediaAction) : LibraryEvent

    data class ArtistMenu(val artist: Artist, val action: MediaAction) : LibraryEvent

    data class SongMenu(val song: Song, val action: MediaAction) : LibraryEvent

    data object MessageShown : LibraryEvent

    data object ToggleDownloaded : LibraryEvent
}

/** Library tab root (DESIGN.md §4.2). Stateful wrapper around [LibraryContent]. */
@Suppress("LongParameterList")
@Composable
fun LibraryScreen(
    onAccountClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onDownloadsClick: () -> Unit,
    playActions: PlayActions,
    onAddToPlaylist: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    downloadsTab: (@Composable () -> Unit)? = null,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    val songs = viewModel.songs.collectAsLazyPagingItems()
    LibraryContent(
        state = state,
        albums = albums,
        artists = artists,
        songs = songs,
        onEvent = { event ->
            when (event) {
                is LibraryEvent.SelectTab -> viewModel.selectTab(event.tab)

                is LibraryEvent.SetSort -> viewModel.setSort(event.sort)

                LibraryEvent.ToggleViewMode -> viewModel.toggleViewMode()

                LibraryEvent.ToggleFavorites -> viewModel.toggleFavoritesFilter()

                is LibraryEvent.SetGenreFilter -> viewModel.setGenreFilter(event.genre)

                is LibraryEvent.SetYearFilter -> viewModel.setYearFilter(event.year)

                LibraryEvent.ClearFilters -> viewModel.clearFilters()

                LibraryEvent.Refresh -> viewModel.refresh()

                LibraryEvent.ShuffleAll -> viewModel.shuffleAll(playActions)

                is LibraryEvent.PlayAlbums -> viewModel.playAlbums(event.order, event.placement, playActions)

                is LibraryEvent.ShuffleGenreAlbums -> playActions.shuffleAlbums(AlbumSource.Genre(event.genre))

                LibraryEvent.OpenAccount -> onAccountClick()

                LibraryEvent.OpenDownloads -> onDownloadsClick()

                is LibraryEvent.OpenAlbum -> onAlbumClick(event.id)

                is LibraryEvent.OpenArtist -> onArtistClick(event.id)

                is LibraryEvent.OpenPlaylist -> onPlaylistClick(event.id)

                is LibraryEvent.OpenGenre -> onGenreClick(event.name)

                is LibraryEvent.PlaySong -> playActions.onPlaySongs(listOf(event.song), 0, false)

                is LibraryEvent.AlbumMenu ->
                    viewModel.onAlbumAction(event.album, event.action, playActions, onAddToPlaylist)

                is LibraryEvent.ArtistMenu -> viewModel.onArtistAction(event.artist, event.action, playActions)

                is LibraryEvent.SongMenu ->
                    viewModel.onSongAction(event.song, event.action, playActions, onAddToPlaylist)

                LibraryEvent.MessageShown -> viewModel.onMessageDismiss()

                LibraryEvent.ToggleDownloaded -> viewModel.toggleDownloadedFilter()
            }
        },
        modifier = modifier,
        downloadsTab = downloadsTab,
    )
}

/** A context menu's subject. */
private sealed interface MenuTarget {
    data class ForAlbum(val album: Album) : MenuTarget

    data class ForArtist(val artist: Artist) : MenuTarget

    data class ForSong(val song: Song) : MenuTarget
}

private enum class LibraryPicker {
    Sort,
    Year,
    Genre,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    albums: LazyPagingItems<Album>,
    artists: LazyPagingItems<Artist>,
    songs: LazyPagingItems<Song>,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier,
    downloadsTab: (@Composable () -> Unit)? = null,
) {
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var picker by remember { mutableStateOf<LibraryPicker?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.text) }
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            currentOnEvent(LibraryEvent.MessageShown)
        }
    }

    val collapse = rememberCollapseOnScrollState()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LibraryTopBar(state, onEvent, onSortClick = { picker = LibraryPicker.Sort })
            LibraryTabRow(selected = state.tab, onSelect = { onEvent(LibraryEvent.SelectTab(it)) })
            SyncStatusBar(progress = state.syncProgress, onRetryClick = { onEvent(LibraryEvent.Refresh) })
            if (state.tab != LibraryTab.Downloads) {
                FilterRow(state, onEvent, onPickerClick = { picker = it })
            }
            if (state.tab == LibraryTab.Downloads && downloadsTab != null) {
                Box(Modifier.weight(1f).fillMaxWidth()) { downloadsTab() }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onEvent(LibraryEvent.Refresh) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(collapse.connection),
                ) {
                    TabContent(
                        state = state,
                        albums = albums,
                        artists = artists,
                        songs = songs,
                        onEvent = onEvent,
                        onMenu = { menu = it },
                    )
                }
            }
        }
        FloatingAlbumPlayButtons(
            visible = state.tab == LibraryTab.Albums && (state.albumCount ?: 0) > 0,
            expanded = collapse.expanded,
            onPlay = { order, placement -> onEvent(LibraryEvent.PlayAlbums(order, placement)) },
        )
        SegueSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    menu?.let { target -> LibraryMenu(target, state, onEvent, onDismissRequest = { menu = null }) }
    picker?.let { which -> LibraryPickerSheet(which, state, onEvent, onDismissRequest = { picker = null }) }
}

@Composable
private fun LibraryTopBar(state: LibraryUiState, onEvent: (LibraryEvent) -> Unit, onSortClick: () -> Unit) {
    TopLevelTopBar(title = stringResource(R.string.feature_library_title)) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        if (state.tab == LibraryTab.Albums) {
            SegueIconButton(
                icon = SegueIcons.Sort,
                contentDescription = stringResource(R.string.feature_library_sort),
                onClick = onSortClick,
                tint = muted,
            )
        }
        if (state.tab == LibraryTab.Albums || state.tab == LibraryTab.Artists) {
            SegueIconButton(
                icon = if (state.viewMode == LibraryViewMode.Grid) SegueIcons.ListView else SegueIcons.GridView,
                contentDescription = stringResource(R.string.feature_library_view_mode),
                onClick = { onEvent(LibraryEvent.ToggleViewMode) },
                tint = muted,
            )
        }
        AccountAction(serverName = state.serverName, onClick = { onEvent(LibraryEvent.OpenAccount) })
    }
}

@Composable
private fun LibraryTabRow(selected: LibraryTab, onSelect: (LibraryTab) -> Unit) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = SegueSpacing.sm,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selected.ordinal, matchContentSize = true),
                width = Dp.Unspecified,
                height = TAB_INDICATOR_HEIGHT,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
    ) {
        LibraryTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

private val TAB_INDICATOR_HEIGHT = 2.dp

/** The 48-tall filter row (DESIGN.md §4.2): chips on the left, the item count on the right. */
@Composable
private fun FilterRow(state: LibraryUiState, onEvent: (LibraryEvent) -> Unit, onPickerClick: (LibraryPicker) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SegueSizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = SegueSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.tab) {
                LibraryTab.Albums -> AlbumFilterChips(state, onEvent, onPickerClick)

                LibraryTab.Songs -> ActionChip(
                    label = stringResource(R.string.feature_library_shuffle_all),
                    icon = SegueIcons.Shuffle,
                    onClick = { onEvent(LibraryEvent.ShuffleAll) },
                )

                else -> Unit
            }
        }
        itemCountLabel(state)?.let { count ->
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                // A gap, so chips scrolled under the count's edge never run into it.
                modifier = Modifier.padding(start = SegueSpacing.sm, end = SegueSpacing.lg),
            )
        }
    }
}

@Composable
private fun RowScope.AlbumFilterChips(
    state: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit,
    onPickerClick: (LibraryPicker) -> Unit,
) {
    DropdownChip(
        label = stringResource(state.sort.label),
        selected = false,
        onClick = { onPickerClick(LibraryPicker.Sort) },
    )
    SegueFilterChip(
        label = stringResource(R.string.feature_library_filter_favorites),
        selected = state.filter.starredOnly,
        onClick = { onEvent(LibraryEvent.ToggleFavorites) },
    )
    // Offline keeps it on: only downloaded albums can play (DESIGN.md §4.2 filter row).
    SegueFilterChip(
        label = stringResource(R.string.feature_library_filter_downloaded),
        selected = state.showsDownloadedOnly,
        onClick = { if (!state.isOffline) onEvent(LibraryEvent.ToggleDownloaded) },
    )
    DropdownChip(
        label = state.filter.year?.toString() ?: stringResource(R.string.feature_library_filter_year),
        selected = state.filter.year != null,
        onClick = { onPickerClick(LibraryPicker.Year) },
    )
    DropdownChip(
        label = state.filter.genre ?: stringResource(R.string.feature_library_filter_genre),
        selected = state.filter.genre != null,
        onClick = { onPickerClick(LibraryPicker.Genre) },
    )
}

/** A one-shot action in the filter row ("Shuffle all"), shaped like the filter chips. */
@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
        },
        shape = SegueShapes.full,
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            borderWidth = SegueSizes.hairline,
        ),
        modifier = modifier.height(SegueSizes.chip),
    )
}

@Composable
private fun itemCountLabel(state: LibraryUiState): String? {
    val format = NumberFormat.getIntegerInstance()
    return when (state.tab) {
        LibraryTab.Albums -> state.albumCount?.let {
            pluralStringResource(R.plurals.feature_library_album_count, it, format.format(it))
        }

        LibraryTab.Artists -> state.artistCount?.let {
            pluralStringResource(R.plurals.feature_library_artist_count, it, format.format(it))
        }

        LibraryTab.Songs -> state.songCount?.let {
            pluralStringResource(R.plurals.feature_library_song_count, it, format.format(it))
        }

        LibraryTab.Playlists -> state.playlists?.size?.let {
            pluralStringResource(R.plurals.feature_library_playlist_count, it, format.format(it))
        }

        LibraryTab.Genres -> state.genres.size.takeIf { state.isLoaded }?.let {
            pluralStringResource(R.plurals.feature_library_genre_count, it, format.format(it))
        }

        LibraryTab.Downloads -> null
    }
}

@Composable
private fun TabContent(
    state: LibraryUiState,
    albums: LazyPagingItems<Album>,
    artists: LazyPagingItems<Artist>,
    songs: LazyPagingItems<Song>,
    onEvent: (LibraryEvent) -> Unit,
    onMenu: (MenuTarget) -> Unit,
) {
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    val gridPadding = PaddingValues(
        start = SegueSpacing.lg,
        end = SegueSpacing.lg,
        top = SegueSpacing.sm,
        bottom = bottom + SegueSpacing.lg,
    )
    val listPadding = PaddingValues(bottom = bottom)
    // The Albums tab keeps its last items clear of the floating Play / Shuffle albums buttons.
    val albumGridPadding = PaddingValues(
        start = SegueSpacing.lg,
        end = SegueSpacing.lg,
        top = SegueSpacing.sm,
        bottom = bottom + FloatingAlbumPlayButtonsInset,
    )
    val albumListPadding = PaddingValues(bottom = bottom + FloatingAlbumPlayButtonsInset)
    val albumGridState = rememberLazyGridState()
    val albumListState = rememberLazyListState()
    PinAlbumsToTopOnViewChange(
        view = AlbumView(state.sort, state.filter, state.isOffline),
        albums = albums,
        gridState = albumGridState,
        listState = albumListState,
    )
    when (state.tab) {
        LibraryTab.Albums -> when {
            state.albumCount == 0 && state.isOffline -> OfflineEmpty(listPadding)

            state.albumCount == 0 && state.filter.isActive -> ScrollableEmpty(listPadding) {
                EmptyState(
                    icon = SegueIcons.Disc3,
                    title = stringResource(R.string.feature_library_empty_filtered_title),
                    message = stringResource(R.string.feature_library_empty_filtered_message),
                    actionLabel = stringResource(R.string.feature_library_clear_filters),
                    onActionClick = { onEvent(LibraryEvent.ClearFilters) },
                )
            }

            state.albumCount == 0 -> LibraryEmpty(listPadding, onEvent)

            state.viewMode == LibraryViewMode.Grid -> PagedAlbumGrid(
                albums = albums,
                onAlbumClick = { onEvent(LibraryEvent.OpenAlbum(it.id)) },
                onAlbumLongClick = { onMenu(MenuTarget.ForAlbum(it)) },
                contentPadding = albumGridPadding,
                modifier = Modifier.fillMaxSize(),
                downloads = state.pinnedAlbums,
                state = albumGridState,
            )

            else -> LazyColumn(
                state = albumListState,
                contentPadding = albumListPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = albums.itemCount,
                    key = albums.itemKey { it.id },
                    contentType = albums.itemContentType { "album" },
                ) { index ->
                    val album = albums[index]
                    MediaRow(
                        title = album?.name.orEmpty(),
                        subtitle = album?.artist,
                        coverArtId = album?.coverArtId,
                        onClick = { album?.let { onEvent(LibraryEvent.OpenAlbum(it.id)) } },
                        onLongClick = album?.let { { onMenu(MenuTarget.ForAlbum(it)) } },
                        onMoreClick = album?.let { { onMenu(MenuTarget.ForAlbum(it)) } },
                        download = album?.let { state.pinnedAlbums[it.id]?.toBadgeState() },
                    )
                }
            }
        }

        LibraryTab.Artists -> when {
            state.isOffline && artists.itemCount == 0 -> OfflineEmpty(listPadding)
            state.artistCount == 0 -> LibraryEmpty(listPadding, onEvent)
            state.viewMode == LibraryViewMode.Grid -> ArtistGrid(artists, gridPadding, onEvent, onMenu)
            else -> ArtistList(artists, listPadding, onEvent, onMenu, state.pinnedArtists)
        }

        LibraryTab.Songs -> when {
            state.songCount == 0 && state.isOffline -> OfflineEmpty(listPadding)
            state.songCount == 0 -> LibraryEmpty(listPadding, onEvent)
            else -> SongList(songs, listPadding, onEvent, onMenu, state.songDownloads)
        }

        LibraryTab.Playlists -> if (state.isOffline && state.playlists?.isEmpty() == true) {
            OfflineEmpty(listPadding)
        } else {
            PlaylistList(state.playlists, listPadding, onEvent, state.pinnedPlaylists)
        }

        LibraryTab.Genres -> GenreGrid(state, gridPadding, listPadding, onEvent)

        LibraryTab.Downloads -> ScrollableEmpty(listPadding) {
            EmptyState(
                icon = SegueIcons.Download,
                title = stringResource(R.string.feature_library_downloads_title),
                message = stringResource(R.string.feature_library_downloads_message),
                actionLabel = stringResource(R.string.feature_library_open_downloads),
                onActionClick = { onEvent(LibraryEvent.OpenDownloads) },
            )
        }
    }
}

/** What the Albums tab shows: a change of any of it starts the tab back at the top. */
private data class AlbumView(val sort: AlbumSortOrder, val filter: AlbumFilter, val isOffline: Boolean)

/**
 * A new sort or filter starts the Albums tab at the top, where "Play albums" starts too.
 *
 * The new albums land a few frames after [view] changes, and a lazy grid keeps its first visible
 * item in view across a data change, so a reset at the change alone would follow the album that
 * was showing to its spot in the new order. So after a change the albums stay pinned to the top
 * through every update until the user scrolls.
 */
@Composable
private fun PinAlbumsToTopOnViewChange(
    view: AlbumView,
    albums: LazyPagingItems<Album>,
    gridState: LazyGridState,
    listState: LazyListState,
) {
    val pin = remember { AlbumTopPin(view) }
    val shown = albums.itemSnapshotList
    val scrolling = gridState.isScrollInProgress || listState.isScrollInProgress
    // A side effect runs before this frame's layout, so the request wins over the grid's anchoring.
    SideEffect {
        if (pin.holdsTop(view, shown, scrolling)) {
            gridState.requestScrollToItem(0)
            listState.requestScrollToItem(0)
        }
    }
}

private class AlbumTopPin(private var view: AlbumView) {
    private var pinned = false
    private var shown: ItemSnapshotList<Album>? = null

    /** Whether this frame needs the top again: a new [view], or new albums before the user scrolls. */
    fun holdsTop(view: AlbumView, shown: ItemSnapshotList<Album>, scrolling: Boolean): Boolean {
        val changed = view != this.view || shown !== this.shown
        if (view != this.view) {
            this.view = view
            pinned = true
        } else if (scrolling) {
            pinned = false
        }
        this.shown = shown
        return pinned && changed
    }
}

@Composable
private fun ArtistGrid(
    artists: LazyPagingItems<Artist>,
    contentPadding: PaddingValues,
    onEvent: (LibraryEvent) -> Unit,
    onMenu: (MenuTarget) -> Unit,
) {
    val format = NumberFormat.getIntegerInstance()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(ARTIST_GRID_CELL),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(SegueSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = artists.itemCount,
            key = artists.itemKey { it.id },
            contentType = artists.itemContentType { "artist" },
        ) { index ->
            val artist = artists[index] ?: return@items
            ArtistCard(
                state = artist.toCardState(
                    pluralStringResource(
                        R.plurals.feature_library_album_count,
                        artist.albumCount,
                        format.format(artist.albumCount),
                    ),
                ),
                onClick = { onEvent(LibraryEvent.OpenArtist(artist.id)) },
                onLongClick = { onMenu(MenuTarget.ForArtist(artist)) },
                artDisplaySize = ARTIST_GRID_CELL,
            )
        }
    }
}

private val ARTIST_GRID_CELL = 112.dp

@Composable
private fun ArtistList(
    artists: LazyPagingItems<Artist>,
    contentPadding: PaddingValues,
    onEvent: (LibraryEvent) -> Unit,
    onMenu: (MenuTarget) -> Unit,
    downloads: ImmutableMap<String, DownloadStatus>,
) {
    val format = NumberFormat.getIntegerInstance()
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(
            count = artists.itemCount,
            key = artists.itemKey { it.id },
            contentType = artists.itemContentType { "artist" },
        ) { index ->
            val artist = artists[index]
            MediaRow(
                title = artist?.name.orEmpty(),
                subtitle = artist?.let {
                    pluralStringResource(
                        R.plurals.feature_library_album_count,
                        it.albumCount,
                        format.format(it.albumCount),
                    )
                },
                coverArtId = artist?.coverArtId,
                onClick = { artist?.let { onEvent(LibraryEvent.OpenArtist(it.id)) } },
                onLongClick = artist?.let { { onMenu(MenuTarget.ForArtist(it)) } },
                artShape = SegueShapes.full,
                placeholderIcon = SegueIcons.Artist,
                download = artist?.let { downloads[it.id]?.toBadgeState() },
            )
        }
    }
}

@Composable
private fun SongList(
    songs: LazyPagingItems<Song>,
    contentPadding: PaddingValues,
    onEvent: (LibraryEvent) -> Unit,
    onMenu: (MenuTarget) -> Unit,
    downloads: ImmutableMap<String, SongDownload>,
) {
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(
            count = songs.itemCount,
            key = songs.itemKey { it.id },
            contentType = songs.itemContentType { "song" },
        ) { index ->
            val song = songs[index]
            if (song == null) {
                androidx.compose.foundation.layout.Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(SegueSizes.trackRow),
                )
            } else {
                val download = downloads[song.id]
                TrackRow(
                    state = remember(song, download) {
                        song.toTrackRowState(download = download?.status?.toBadgeState())
                    },
                    onClick = { onEvent(LibraryEvent.PlaySong(song)) },
                    onLongClick = { onMenu(MenuTarget.ForSong(song)) },
                    onMoreClick = { onMenu(MenuTarget.ForSong(song)) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: kotlinx.collections.immutable.ImmutableList<Playlist>?,
    contentPadding: PaddingValues,
    onEvent: (LibraryEvent) -> Unit,
    downloads: ImmutableMap<String, DownloadStatus>,
) {
    when {
        playlists == null -> Unit

        playlists.isEmpty() -> ScrollableEmpty(contentPadding) {
            EmptyState(
                icon = SegueIcons.Playlists,
                title = stringResource(R.string.feature_library_empty_playlists_title),
                message = stringResource(R.string.feature_library_empty_playlists_message),
            )
        }

        else -> LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                MediaRow(
                    title = playlist.name,
                    subtitle = playlistSubtitle(playlist),
                    coverArtId = playlist.coverArtId,
                    onClick = { onEvent(LibraryEvent.OpenPlaylist(playlist.id)) },
                    placeholderIcon = SegueIcons.Playlists,
                    download = downloads[playlist.id]?.toBadgeState(),
                )
            }
        }
    }
}

@Composable
private fun GenreGrid(
    state: LibraryUiState,
    gridPadding: PaddingValues,
    listPadding: PaddingValues,
    onEvent: (LibraryEvent) -> Unit,
) {
    if (!state.isLoaded) return
    if (state.genres.isEmpty()) {
        ScrollableEmpty(listPadding) {
            EmptyState(
                icon = SegueIcons.Music,
                title = stringResource(R.string.feature_library_empty_genres_title),
                message = stringResource(R.string.feature_library_empty_message),
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GENRE_COLUMNS),
        contentPadding = gridPadding,
        horizontalArrangement = Arrangement.spacedBy(SegueSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(SegueSpacing.gutter),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.genres, key = { it.name }, contentType = { "genre" }) { genre ->
            GenreTile(
                genre = genre,
                onClick = { onEvent(LibraryEvent.OpenGenre(genre.name)) },
                onShuffleAlbumsClick = { onEvent(LibraryEvent.ShuffleGenreAlbums(genre.name)) },
            )
        }
    }
}

private const val GENRE_COLUMNS = 2

/** Offline with nothing downloaded in this tab. */
@Composable
private fun OfflineEmpty(contentPadding: PaddingValues) {
    ScrollableEmpty(contentPadding) {
        EmptyState(
            icon = SegueIcons.Offline,
            title = stringResource(R.string.feature_library_offline_empty_title),
            message = stringResource(R.string.feature_library_offline_empty_message),
        )
    }
}

@Composable
private fun LibraryEmpty(contentPadding: PaddingValues, onEvent: (LibraryEvent) -> Unit) {
    ScrollableEmpty(contentPadding) {
        EmptyState(
            icon = SegueIcons.Library,
            title = stringResource(R.string.feature_library_empty_title),
            message = stringResource(R.string.feature_library_empty_message),
            actionLabel = stringResource(R.string.feature_library_sync_now),
            onActionClick = { onEvent(LibraryEvent.Refresh) },
        )
    }
}

/** An empty state inside a scrollable, so pull-to-refresh still works on it. */
@Composable
private fun ScrollableEmpty(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        emptyItem(content)
    }
}

private fun LazyListScope.emptyItem(content: @Composable () -> Unit) {
    item(key = "empty", contentType = "empty") {
        Box(Modifier.fillParentMaxSize()) { content() }
    }
}

@Composable
private fun LibraryMenu(
    target: MenuTarget,
    state: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    when (target) {
        is MenuTarget.ForAlbum -> {
            val album = target.album
            MediaContextMenu(
                header = album.toMenuHeader(),
                actions = MediaActions.album(
                    isStarred = album.isStarred,
                    hasArtist = album.artistId != null,
                    isDownloaded = album.id in state.pinnedAlbums,
                ),
                onAction = { action ->
                    when (action) {
                        MediaAction.GoToArtist -> album.artistId?.let { onEvent(LibraryEvent.OpenArtist(it)) }
                        else -> onEvent(LibraryEvent.AlbumMenu(album, action))
                    }
                },
                onDismissRequest = onDismissRequest,
            )
        }

        is MenuTarget.ForArtist -> MediaContextMenu(
            header = target.artist.toMenuHeader(),
            actions = MediaActions.artist(
                isStarred = target.artist.isStarred,
                isDownloaded = target.artist.id in state.pinnedArtists,
            ),
            onAction = { onEvent(LibraryEvent.ArtistMenu(target.artist, it)) },
            onDismissRequest = onDismissRequest,
        )

        is MenuTarget.ForSong -> {
            val song = target.song
            MediaContextMenu(
                header = song.toMenuHeader(),
                actions = MediaActions.song(
                    isStarred = song.isStarred,
                    hasAlbum = song.albumId != null,
                    hasArtist = song.artistId != null,
                ),
                onAction = { action ->
                    when (action) {
                        MediaAction.GoToAlbum -> song.albumId?.let { onEvent(LibraryEvent.OpenAlbum(it)) }
                        MediaAction.GoToArtist -> song.artistId?.let { onEvent(LibraryEvent.OpenArtist(it)) }
                        else -> onEvent(LibraryEvent.SongMenu(song, action))
                    }
                },
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

@Composable
private fun LibraryPickerSheet(
    picker: LibraryPicker,
    state: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val any = stringResource(R.string.feature_library_filter_any)
    when (picker) {
        LibraryPicker.Sort -> {
            val labels = AlbumSortOrder.entries.map { stringResource(it.label) }
            PickerSheet(
                title = stringResource(R.string.feature_library_sort),
                options = AlbumSortOrder.entries.mapIndexed { i, sort -> PickerOption(sort.name, labels[i]) }
                    .toImmutableList(),
                selectedKey = state.sort.name,
                onSelect = { key -> onEvent(LibraryEvent.SetSort(AlbumSortOrder.valueOf(key))) },
                onDismissRequest = onDismissRequest,
            )
        }

        LibraryPicker.Year -> PickerSheet(
            title = stringResource(R.string.feature_library_filter_year),
            options = (
                listOf(PickerOption(ANY_KEY, any)) +
                    state.years.map { PickerOption(it.toString(), it.toString()) }
                )
                .toImmutableList(),
            selectedKey = state.filter.year?.toString() ?: ANY_KEY,
            onSelect = { key -> onEvent(LibraryEvent.SetYearFilter(key.toIntOrNull())) },
            onDismissRequest = onDismissRequest,
        )

        LibraryPicker.Genre -> PickerSheet(
            title = stringResource(R.string.feature_library_filter_genre),
            options = (
                listOf(PickerOption(ANY_KEY, any)) +
                    state.genres.map { PickerOption(GENRE_PREFIX + it.name, it.name) }
                )
                .toImmutableList(),
            selectedKey = state.filter.genre?.let { GENRE_PREFIX + it } ?: ANY_KEY,
            onSelect = { key ->
                onEvent(LibraryEvent.SetGenreFilter(key.takeIf { it != ANY_KEY }?.removePrefix(GENRE_PREFIX)))
            },
            onDismissRequest = onDismissRequest,
        )
    }
}

/** Picker key for "no filter"; genre keys are prefixed so a genre can never collide with it. */
private const val ANY_KEY = "any"
private const val GENRE_PREFIX = "genre:"

internal val LibraryUiState.isLoaded: Boolean get() = albumCount != null

private val LibraryTab.label: Int
    get() = when (this) {
        LibraryTab.Albums -> R.string.feature_library_tab_albums
        LibraryTab.Artists -> R.string.feature_library_tab_artists
        LibraryTab.Songs -> R.string.feature_library_tab_songs
        LibraryTab.Playlists -> R.string.feature_library_tab_playlists
        LibraryTab.Genres -> R.string.feature_library_tab_genres
        LibraryTab.Downloads -> R.string.feature_library_tab_downloads
    }

internal val AlbumSortOrder.label: Int
    get() = when (this) {
        AlbumSortOrder.Name -> R.string.feature_library_sort_name
        AlbumSortOrder.Artist -> R.string.feature_library_sort_artist
        AlbumSortOrder.RecentlyAdded -> R.string.feature_library_sort_recently_added
        AlbumSortOrder.Year -> R.string.feature_library_sort_year
    }

private val LibraryMessage.text: Int
    get() = when (this) {
        LibraryMessage.LoadFailed -> R.string.feature_library_error_load
        LibraryMessage.FavoriteFailed -> R.string.feature_library_error_favorite
        LibraryMessage.RefreshFailed -> R.string.feature_library_error_refresh
        LibraryMessage.NothingToPlay -> R.string.feature_library_error_nothing_to_play
    }

private val PreviewAlbums = List(6) { index ->
    Album(
        id = "al-$index",
        name = listOf("In Rainbows", "Kid A", "OK Computer", "Amnesiac", "Hail to the Thief", "The Bends")[index],
        artist = "Radiohead",
        artistId = "ar-1",
        artists = persistentListOf(),
        coverArtId = null,
        songCount = 10,
        durationSeconds = 2_520,
        year = 2000 + index,
        genre = "Alternative",
        createdAt = null,
        starredAt = null,
        userRating = null,
        playCount = null,
        playedAt = null,
        isCompilation = false,
        discTitles = persistentListOf(),
        musicBrainzId = null,
        explicitStatus = ExplicitStatus.None,
    )
}

@Composable
private fun <T : Any> previewItems(items: List<T>): LazyPagingItems<T> =
    remember(items) { flowOf(PagingData.from(items)) }.collectAsLazyPagingItems()

@PreviewSegueThemes
@Composable
private fun LibraryContentPreview() {
    SeguePreview {
        LibraryContent(
            state = LibraryUiState(
                albumCount = PreviewAlbums.size,
                artistCount = 1,
                songCount = 60,
                genres = persistentListOf(Genre("Alternative", 60, 6)),
                playlists = persistentListOf(),
                filter = AlbumFilter(starredOnly = true),
            ),
            albums = previewItems(PreviewAlbums),
            artists = previewItems(emptyList()),
            songs = previewItems(emptyList()),
            onEvent = {},
        )
    }
}

@PreviewSegueThemes
@Composable
private fun LibraryGenresPreview() {
    SeguePreview {
        LibraryContent(
            state = LibraryUiState(
                tab = LibraryTab.Genres,
                albumCount = 3,
                genres = persistentListOf(
                    Genre("Alternative", 60, 6),
                    Genre("Electronic", 30, 3),
                    Genre("Jazz", 10, 1),
                ),
            ),
            albums = previewItems(emptyList()),
            artists = previewItems(emptyList()),
            songs = previewItems(emptyList()),
            onEvent = {},
        )
    }
}
