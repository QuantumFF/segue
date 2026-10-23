package com.quantumff.segue.feature.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.LazyGridScope
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.designsystem.component.AccountAction
import com.quantumff.segue.core.designsystem.component.ArtistCard
import com.quantumff.segue.core.designsystem.component.ContextMenuHeader
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtons
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtonsInset
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.MediaRow
import com.quantumff.segue.core.designsystem.component.SegueFilterChip
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.SelectionTopBar
import com.quantumff.segue.core.designsystem.component.SyncStatusBar
import com.quantumff.segue.core.designsystem.component.TopLevelTopBar
import com.quantumff.segue.core.designsystem.component.TrackRow
import com.quantumff.segue.core.designsystem.component.TrackSwipeBox
import com.quantumff.segue.core.designsystem.component.rememberCollapseOnScrollState
import com.quantumff.segue.core.designsystem.component.text
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
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ArtistSortOrder
import com.quantumff.segue.core.model.CurrentSong
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.ExplicitStatus
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.GridSize
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActions
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongDownload
import java.text.NumberFormat
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf

/** What the Library screen asks of its host or its ViewModel. */
internal sealed interface LibraryEvent {
    data class SelectTab(val tab: LibraryTab) : LibraryEvent

    data class SetAlbumSort(val sort: AlbumSortOrder) : LibraryEvent

    data class SetArtistSort(val sort: ArtistSortOrder) : LibraryEvent

    data object ToggleViewMode : LibraryEvent

    /** The [tab]'s Favorites chip was tapped. */
    data class CycleFavorites(val tab: LibraryTab) : LibraryEvent

    data class SetGenreFilter(val genre: String?) : LibraryEvent

    data class SetYearFilter(val year: Int?) : LibraryEvent

    /** Turns off the [tab]'s filters. */
    data class ClearFilters(val tab: LibraryTab) : LibraryEvent

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

    /** The [tab]'s Downloaded chip was tapped. */
    data class CycleDownloaded(val tab: LibraryTab) : LibraryEvent

    /** Starts multi-select with the item, or picks or drops it. */
    data class ToggleSelected(val item: LibraryItem) : LibraryEvent

    /** A drag after a long-press picks or drops an item, as [picked] says. */
    data class SetPicked(val item: LibraryItem, val picked: Boolean) : LibraryEvent

    data object ClearSelection : LibraryEvent

    /** A pinch stepped the [tab]'s grid to cards of [size]. */
    data class SetGridSize(val tab: LibraryTab, val size: GridSize) : LibraryEvent

    /** A selection menu action, on every pick. */
    data class SelectionAction(val action: MediaAction) : LibraryEvent
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
    onAddToPlaylist: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    downloadsTab: (@Composable () -> Unit)? = null,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val menuHost = remember(onAlbumClick, onArtistClick, onAddToPlaylist) {
        MediaActionHost(onAlbumClick = onAlbumClick, onArtistClick = onArtistClick, onAddToPlaylist = onAddToPlaylist)
    }
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
                is LibraryEvent.SetAlbumSort -> viewModel.setAlbumSort(event.sort)
                is LibraryEvent.SetArtistSort -> viewModel.setArtistSort(event.sort)
                LibraryEvent.ToggleViewMode -> viewModel.toggleViewMode()
                is LibraryEvent.CycleFavorites -> viewModel.cycleFavoritesFilter(event.tab)
                is LibraryEvent.SetGenreFilter -> viewModel.setGenreFilter(event.genre)
                is LibraryEvent.SetYearFilter -> viewModel.setYearFilter(event.year)
                is LibraryEvent.ClearFilters -> viewModel.clearFilters(event.tab)
                LibraryEvent.Refresh -> viewModel.refresh()
                LibraryEvent.ShuffleAll -> viewModel.shuffleAll()
                is LibraryEvent.PlayAlbums -> viewModel.playAlbums(event.order, event.placement)
                is LibraryEvent.ShuffleGenreAlbums -> viewModel.shuffleGenreAlbums(event.genre)
                LibraryEvent.OpenAccount -> onAccountClick()
                LibraryEvent.OpenDownloads -> onDownloadsClick()
                is LibraryEvent.OpenAlbum -> onAlbumClick(event.id)
                is LibraryEvent.OpenArtist -> onArtistClick(event.id)
                is LibraryEvent.OpenPlaylist -> onPlaylistClick(event.id)
                is LibraryEvent.OpenGenre -> onGenreClick(event.name)
                is LibraryEvent.PlaySong -> viewModel.playSong(event.song)
                is LibraryEvent.AlbumMenu -> viewModel.onAlbumAction(event.album, event.action, menuHost)
                is LibraryEvent.ArtistMenu -> viewModel.onArtistAction(event.artist, event.action, menuHost)
                is LibraryEvent.SongMenu -> viewModel.onSongAction(event.song, event.action, menuHost)
                LibraryEvent.MessageShown -> viewModel.onMessageDismiss()
                is LibraryEvent.CycleDownloaded -> viewModel.cycleDownloadedFilter(event.tab)
                is LibraryEvent.ToggleSelected -> viewModel.toggleSelected(event.item)
                is LibraryEvent.SetPicked -> viewModel.setPicked(event.item, event.picked)
                LibraryEvent.ClearSelection -> viewModel.clearSelection()
                is LibraryEvent.SetGridSize -> viewModel.setGridSize(event.tab, event.size)
                is LibraryEvent.SelectionAction -> viewModel.onSelectionAction(event.action, menuHost)
            }
        },
        modifier = modifier,
        downloadsTab = downloadsTab,
    )
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
    var menu by remember { mutableStateOf<LibraryItem?>(null) }
    var picker by remember { mutableStateOf<LibraryPicker?>(null) }
    var selectionMenu by remember { mutableStateOf(false) }
    val selection = state.selection
    BackHandler(enabled = selection != null) { onEvent(LibraryEvent.ClearSelection) }
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
            if (selection != null) {
                LibrarySelectionBar(selection, onEvent, onMoreClick = { selectionMenu = true })
            } else {
                LibraryTopBar(state, onEvent)
            }
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
            visible = state.tab == LibraryTab.Albums && (state.albumCount ?: 0) > 0 && selection == null,
            expanded = collapse.expanded,
            onPlay = { order, placement -> onEvent(LibraryEvent.PlayAlbums(order, placement)) },
        )
        SegueSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    menu?.let { target -> LibraryMenu(target, state, onEvent, onDismissRequest = { menu = null }) }
    picker?.let { which -> LibraryPickerSheet(which, state, onEvent, onDismissRequest = { picker = null }) }
    if (selectionMenu && selection != null) {
        SelectionMenu(selection, state, onEvent, onDismissRequest = { selectionMenu = false })
    }
}

/** Multi-select's bar (DESIGN.md §4.2): the count, then Play, Add to queue and the selection menu. */
@Composable
private fun LibrarySelectionBar(selection: LibrarySelection, onEvent: (LibraryEvent) -> Unit, onMoreClick: () -> Unit) {
    val count = selection.ids.size
    SelectionTopBar(
        title = pluralStringResource(
            R.plurals.feature_library_selected_count,
            count,
            NumberFormat.getIntegerInstance().format(count),
        ),
        onCloseClick = { onEvent(LibraryEvent.ClearSelection) },
    ) {
        SegueIconButton(
            icon = SegueIcons.Play,
            contentDescription = stringResource(R.string.feature_library_play_selected),
            onClick = { onEvent(LibraryEvent.SelectionAction(MediaAction.Play)) },
        )
        SegueIconButton(
            icon = SegueIcons.AddToQueue,
            contentDescription = stringResource(R.string.feature_library_queue_selected),
            onClick = { onEvent(LibraryEvent.SelectionAction(MediaAction.AddToQueue)) },
        )
        SegueIconButton(
            icon = SegueIcons.MoreVertical,
            contentDescription = stringResource(R.string.feature_library_selection_options),
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun LibraryTopBar(state: LibraryUiState, onEvent: (LibraryEvent) -> Unit) {
    // Sorting lives on each tab's sort chip, not here (DESIGN.md §4.2).
    TopLevelTopBar(title = stringResource(R.string.feature_library_title)) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
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

                LibraryTab.Artists -> {
                    SortChip(state, onClick = { onPickerClick(LibraryPicker.Sort) })
                    FavoritesDownloadedChips(state, LibraryTab.Artists, onEvent)
                }

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
    SortChip(state, onClick = { onPickerClick(LibraryPicker.Sort) })
    FavoritesDownloadedChips(state, LibraryTab.Albums, onEvent)
    DropdownChip(
        label = state.albumFilter.year?.toString() ?: stringResource(R.string.feature_library_filter_year),
        selected = state.albumFilter.year != null,
        onClick = { onPickerClick(LibraryPicker.Year) },
    )
    DropdownChip(
        label = state.albumFilter.genre ?: stringResource(R.string.feature_library_filter_genre),
        selected = state.albumFilter.genre != null,
        onClick = { onPickerClick(LibraryPicker.Genre) },
    )
}

/** The sort chip of the tab on show, labelled with its sort; it opens that tab's sort picker. */
@Composable
private fun SortChip(state: LibraryUiState, onClick: () -> Unit) {
    val sort = tabSort(state) ?: return
    DropdownChip(label = stringResource(sort.currentLabel), selected = false, onClick = onClick)
}

/**
 * The Favorites and Downloaded chips of [tab], shared by every tab that has them (DESIGN.md §4.2).
 * Each cycles through on, inverted and off; offline holds Downloaded on, since only downloaded
 * music plays.
 */
@Composable
private fun FavoritesDownloadedChips(state: LibraryUiState, tab: LibraryTab, onEvent: (LibraryEvent) -> Unit) {
    val chips = state.chipFilters(tab) ?: return
    SegueFilterChip(
        label = stringResource(R.string.feature_library_filter_favorites),
        selected = chips.favorites != null,
        excluded = chips.favorites == false,
        onClick = { onEvent(LibraryEvent.CycleFavorites(tab)) },
    )
    SegueFilterChip(
        label = stringResource(R.string.feature_library_filter_downloaded),
        selected = chips.shownDownloaded != null,
        excluded = chips.shownDownloaded == false,
        onClick = { if (!chips.isOffline) onEvent(LibraryEvent.CycleDownloaded(tab)) },
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
    onMenu: (LibraryItem) -> Unit,
) {
    val selected = state.selection?.ids ?: persistentSetOf()
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
    val artistGridState = rememberLazyGridState()
    val artistListState = rememberLazyListState()
    val picker = Picker(selected, remember(state.tab) { DragSelectState() }, onEvent)
    PinToTopOnViewChange(
        view = ListView(state.albumSort, state.albumFilter, state.isOffline),
        items = albums,
        gridState = albumGridState,
        listState = albumListState,
    )
    PinToTopOnViewChange(
        view = ListView(state.artistSort, state.artistFilter, state.isOffline),
        items = artists,
        gridState = artistGridState,
        listState = artistListState,
    )
    when (state.tab) {
        LibraryTab.Albums -> when {
            state.albumCount == 0 && state.isOffline -> OfflineEmpty(listPadding)

            state.albumCount == 0 && state.albumFilter.isActive -> FilteredEmpty(
                icon = SegueIcons.Disc3,
                title = stringResource(R.string.feature_library_empty_filtered_title),
                contentPadding = listPadding,
                onClearClick = { onEvent(LibraryEvent.ClearFilters(LibraryTab.Albums)) },
            )

            state.albumCount == 0 -> LibraryEmpty(listPadding, onEvent)

            state.viewMode == LibraryViewMode.Grid -> PinchResizableGrid(
                size = state.albumGridSize,
                cell = SegueSizes.albumGridCell,
                horizontalPadding = SegueSpacing.lg,
                state = albumGridState,
                onResize = { onEvent(LibraryEvent.SetGridSize(LibraryTab.Albums, it)) },
            ) { layout ->
                PagedAlbumGrid(
                    albums = albums,
                    onAlbumClick = {
                        onEvent(tapEvent(selected, LibraryItem.OfAlbum(it), LibraryEvent.OpenAlbum(it.id)))
                    },
                    onAlbumLongClick = { index, album -> picker.longPress(index, LibraryItem.OfAlbum(album)) },
                    contentPadding = albumGridPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .dragToSelect(
                            state = picker.drag,
                            grid = albumGridState,
                            items = albums,
                            toItem = { LibraryItem.OfAlbum(it) },
                            onPick = picker::pick,
                            startPadding = SegueSpacing.lg,
                        ),
                    downloads = state.pinnedAlbums,
                    state = albumGridState,
                    selected = selected,
                    onAlbumLongClickLabel = stringResource(R.string.feature_library_select),
                    columns = layout.cells,
                    horizontalArrangement = layout.arrangement,
                    artDisplaySize = layout.cellWidth,
                    zoom = layout.zoom,
                )
            }

            else -> LazyColumn(
                state = albumListState,
                contentPadding = albumListPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .dragToSelect(picker.drag, albumListState, albums, { LibraryItem.OfAlbum(it) }, picker::pick),
            ) {
                items(
                    count = albums.itemCount,
                    key = albums.itemKey { it.id },
                    contentType = albums.itemContentType { "album" },
                ) { index ->
                    val album = albums[index]
                    val target = album?.let { LibraryItem.OfAlbum(it) }
                    MediaRow(
                        title = album?.name.orEmpty(),
                        subtitle = album?.artist,
                        coverArtId = album?.coverArtId,
                        onClick = {
                            target?.let { onEvent(tapEvent(selected, it, LibraryEvent.OpenAlbum(it.album.id))) }
                        },
                        onLongClick = target?.let { { picker.longPress(index, it) } },
                        onMoreClick = target?.takeIf { selected.isEmpty() }?.let { { onMenu(it) } },
                        download = album?.let { state.pinnedAlbums[it.id]?.toBadgeState() },
                        selected = target?.id in selected,
                        onLongClickLabel = stringResource(R.string.feature_library_select),
                    )
                }
            }
        }

        LibraryTab.Artists -> when {
            state.isOffline && artists.itemCount == 0 -> OfflineEmpty(listPadding)

            state.artistCount == 0 && state.artistFilter.isActive -> FilteredEmpty(
                icon = SegueIcons.Artist,
                title = stringResource(R.string.feature_library_empty_filtered_artists_title),
                contentPadding = listPadding,
                onClearClick = { onEvent(LibraryEvent.ClearFilters(LibraryTab.Artists)) },
            )

            state.artistCount == 0 -> LibraryEmpty(listPadding, onEvent)

            state.viewMode == LibraryViewMode.Grid -> ArtistGrid(
                artists,
                gridPadding,
                state.artistGridSize,
                artistGridState,
                picker,
                onEvent,
            )

            else -> ArtistList(artists, listPadding, artistListState, onEvent, state.pinnedArtists, picker)
        }

        LibraryTab.Songs -> when {
            state.songCount == 0 && state.isOffline -> OfflineEmpty(listPadding)
            state.songCount == 0 -> LibraryEmpty(listPadding, onEvent)
            else -> SongList(songs, listPadding, onEvent, onMenu, state.songDownloads, state.currentSong, picker)
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

/** What a sorted tab shows: a change of any of it starts the tab back at the top. */
private data class ListView(val sort: Enum<*>, val filter: Any, val isOffline: Boolean)

/**
 * A new sort or filter starts a tab at the top, where "Play albums" starts too.
 *
 * The new items land a few frames after [view] changes, and a lazy grid keeps its first visible
 * item in view across a data change, so a reset at the change alone would follow the item that
 * was showing to its spot in the new order. So after a change the items stay pinned to the top
 * through every update until the user scrolls.
 */
@Composable
private fun PinToTopOnViewChange(
    view: ListView,
    items: LazyPagingItems<*>,
    gridState: LazyGridState,
    listState: LazyListState,
) {
    val pin = remember { TopPin(view) }
    val shown = items.itemSnapshotList
    val scrolling = gridState.isScrollInProgress || listState.isScrollInProgress
    // A side effect runs before this frame's layout, so the request wins over the grid's anchoring.
    SideEffect {
        if (pin.holdsTop(view, shown, scrolling)) {
            gridState.requestScrollToItem(0)
            listState.requestScrollToItem(0)
        }
    }
}

private class TopPin(private var view: ListView) {
    private var pinned = false
    private var shown: ItemSnapshotList<*>? = null

    /** Whether this frame needs the top again: a new [view], or new items before the user scrolls. */
    fun holdsTop(view: ListView, shown: ItemSnapshotList<*>, scrolling: Boolean): Boolean {
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
    size: GridSize,
    gridState: LazyGridState,
    picker: Picker,
    onEvent: (LibraryEvent) -> Unit,
) {
    val format = NumberFormat.getIntegerInstance()
    PinchResizableGrid(
        size = size,
        cell = ARTIST_GRID_CELL,
        horizontalPadding = SegueSpacing.lg,
        state = gridState,
        onResize = { onEvent(LibraryEvent.SetGridSize(LibraryTab.Artists, it)) },
    ) { layout ->
        LazyVerticalGrid(
            columns = layout.cells,
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = layout.arrangement,
            verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg),
            modifier = Modifier
                .fillMaxSize()
                .dragToSelect(
                    state = picker.drag,
                    grid = gridState,
                    items = artists,
                    toItem = { LibraryItem.OfArtist(it) },
                    onPick = picker::pick,
                    startPadding = SegueSpacing.lg,
                ),
        ) {
            artistItems(artists, format, layout.cellWidth, layout.zoom, picker, onEvent)
        }
    }
}

private fun LazyGridScope.artistItems(
    artists: LazyPagingItems<Artist>,
    format: NumberFormat,
    cellWidth: Dp,
    zoom: GridZoom,
    picker: Picker,
    onEvent: (LibraryEvent) -> Unit,
) {
    val selected = picker.selected
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
            onClick = {
                onEvent(tapEvent(selected, LibraryItem.OfArtist(artist), LibraryEvent.OpenArtist(artist.id)))
            },
            onLongClick = { picker.longPress(index, LibraryItem.OfArtist(artist)) },
            artDisplaySize = cellWidth,
            selected = artist.id in selected,
            onLongClickLabel = stringResource(R.string.feature_library_select),
            modifier = Modifier.reflow(zoom, index),
        )
    }
}

private val ARTIST_GRID_CELL = 112.dp

@Composable
private fun ArtistList(
    artists: LazyPagingItems<Artist>,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onEvent: (LibraryEvent) -> Unit,
    downloads: ImmutableMap<String, DownloadStatus>,
    picker: Picker,
) {
    val format = NumberFormat.getIntegerInstance()
    val selected = picker.selected
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxSize()
            .dragToSelect(picker.drag, listState, artists, { LibraryItem.OfArtist(it) }, picker::pick),
    ) {
        items(
            count = artists.itemCount,
            key = artists.itemKey { it.id },
            contentType = artists.itemContentType { "artist" },
        ) { index ->
            val artist = artists[index]
            val target = artist?.let { LibraryItem.OfArtist(it) }
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
                onClick = { target?.let { onEvent(tapEvent(selected, it, LibraryEvent.OpenArtist(it.artist.id))) } },
                onLongClick = target?.let { { picker.longPress(index, it) } },
                artShape = SegueShapes.full,
                placeholderIcon = SegueIcons.Artist,
                download = artist?.let { downloads[it.id]?.toBadgeState() },
                selected = target?.id in selected,
                onLongClickLabel = stringResource(R.string.feature_library_select),
            )
        }
    }
}

@Composable
private fun SongList(
    songs: LazyPagingItems<Song>,
    contentPadding: PaddingValues,
    onEvent: (LibraryEvent) -> Unit,
    onMenu: (LibraryItem) -> Unit,
    downloads: ImmutableMap<String, SongDownload>,
    currentSong: CurrentSong?,
    picker: Picker,
) {
    val selected = picker.selected
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxSize()
            .dragToSelect(picker.drag, listState, songs, { LibraryItem.OfSong(it) }, picker::pick),
    ) {
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
                // Swipes queue the song; while selecting, rows only pick.
                TrackSwipeBox(
                    onPlayNext = { onEvent(LibraryEvent.SongMenu(song, MediaAction.PlayNext)) },
                    onAddToQueue = { onEvent(LibraryEvent.SongMenu(song, MediaAction.AddToQueue)) },
                    enabled = selected.isEmpty(),
                ) {
                    TrackRow(
                        state = remember(song, download, currentSong) {
                            song.toTrackRowState(
                                download = download?.status?.toBadgeState(),
                                current = currentSong,
                            )
                        },
                        onClick = {
                            onEvent(tapEvent(selected, LibraryItem.OfSong(song), LibraryEvent.PlaySong(song)))
                        },
                        onLongClick = { picker.longPress(index, LibraryItem.OfSong(song)) },
                        onMoreClick = if (selected.isEmpty()) ({ onMenu(LibraryItem.OfSong(song)) }) else null,
                        selected = song.id in selected,
                        onLongClickLabel = stringResource(R.string.feature_library_select),
                    )
                }
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

/** A tab whose filters leave nothing, with a way to clear them. */
@Composable
private fun FilteredEmpty(icon: ImageVector, title: String, contentPadding: PaddingValues, onClearClick: () -> Unit) {
    ScrollableEmpty(contentPadding) {
        EmptyState(
            icon = icon,
            title = title,
            message = stringResource(R.string.feature_library_empty_filtered_message),
            actionLabel = stringResource(R.string.feature_library_clear_filters),
            onActionClick = onClearClick,
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
    target: LibraryItem,
    state: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    when (target) {
        is LibraryItem.OfAlbum -> {
            val album = target.album
            MediaContextMenu(
                header = album.toMenuHeader(),
                actions = MediaActions.album(
                    isStarred = album.isStarred,
                    hasArtist = album.artistId != null,
                    isDownloaded = album.id in state.pinnedAlbums,
                ),
                onAction = { onEvent(LibraryEvent.AlbumMenu(album, it)) },
                onDismissRequest = onDismissRequest,
            )
        }

        is LibraryItem.OfArtist -> MediaContextMenu(
            header = target.artist.toMenuHeader(),
            actions = MediaActions.artist(
                isStarred = target.artist.isStarred,
                isDownloaded = target.artist.id in state.pinnedArtists,
            ),
            onAction = { onEvent(LibraryEvent.ArtistMenu(target.artist, it)) },
            onDismissRequest = onDismissRequest,
        )

        is LibraryItem.OfSong -> {
            val song = target.song
            MediaContextMenu(
                header = song.toMenuHeader(),
                actions = MediaActions.song(
                    isStarred = song.isStarred,
                    hasAlbum = song.albumId != null,
                    hasArtist = song.artistId != null,
                ),
                onAction = { onEvent(LibraryEvent.SongMenu(song, it)) },
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

/**
 * Multi-select for a Library list: the [selected] ids, and the long-press that picks or drops an
 * item and lets a drag carry on from it ([drag], DESIGN.md §4.2).
 */
@Stable
private class Picker(
    val selected: ImmutableSet<String>,
    val drag: DragSelectState,
    private val onEvent: (LibraryEvent) -> Unit,
) {
    fun longPress(index: Int, item: LibraryItem) {
        drag.start(index, item.id, selected)
        onEvent(LibraryEvent.ToggleSelected(item))
    }

    fun pick(item: LibraryItem, picked: Boolean) = onEvent(LibraryEvent.SetPicked(item, picked))
}

/** While selecting (anything [selected]), a tap picks or drops [item]; otherwise it is [event]. */
private fun tapEvent(selected: ImmutableSet<String>, item: LibraryItem, event: LibraryEvent): LibraryEvent =
    if (selected.isNotEmpty()) LibraryEvent.ToggleSelected(item) else event

/**
 * The selection's ⋮ (DESIGN.md §4.2): one pick gets its own context menu, so a long-press
 * followed by ⋮ reaches everything the menu used to; several get the menu for all of them. Either
 * way an action ends multi-select.
 */
@Composable
private fun SelectionMenu(
    selection: LibrarySelection,
    state: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val single = when (selection) {
        is LibrarySelection.Albums -> selection.albums.singleOrNull()?.let { LibraryItem.OfAlbum(it) }
        is LibrarySelection.Artists -> selection.artists.singleOrNull()?.let { LibraryItem.OfArtist(it) }
        is LibrarySelection.Songs -> selection.songs.singleOrNull()?.let { LibraryItem.OfSong(it) }
    }
    if (single != null) {
        LibraryMenu(
            target = single,
            state = state,
            onEvent = { event ->
                onEvent(event)
                onEvent(LibraryEvent.ClearSelection)
            },
            onDismissRequest = onDismissRequest,
        )
        return
    }
    MediaContextMenu(
        header = selectionHeader(selection),
        actions = state.selectionActions ?: return,
        onAction = { onEvent(LibraryEvent.SelectionAction(it)) },
        onDismissRequest = onDismissRequest,
    )
}

/** "3 albums" over their names, with the first pick's art. */
@Composable
private fun selectionHeader(selection: LibrarySelection): ContextMenuHeader {
    val count = selection.ids.size
    val formatted = NumberFormat.getIntegerInstance().format(count)
    return when (selection) {
        is LibrarySelection.Albums -> ContextMenuHeader(
            title = pluralStringResource(R.plurals.feature_library_album_count, count, formatted),
            subtitle = selection.albums.joinToString { it.name },
            coverArtId = selection.albums.firstOrNull()?.coverArtId,
        )

        is LibrarySelection.Artists -> ContextMenuHeader(
            title = pluralStringResource(R.plurals.feature_library_artist_count, count, formatted),
            subtitle = selection.artists.joinToString { it.name },
            coverArtId = selection.artists.firstOrNull()?.coverArtId,
            circularArt = true,
        )

        is LibrarySelection.Songs -> ContextMenuHeader(
            title = pluralStringResource(R.plurals.feature_library_song_count, count, formatted),
            subtitle = selection.songs.joinToString { it.title },
            coverArtId = selection.songs.firstOrNull()?.coverArtId,
        )
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
            val sort = tabSort(state) ?: return
            val labels = sort.optionLabels.map { stringResource(it) }
            PickerSheet(
                title = stringResource(R.string.feature_library_sort),
                options = sort.optionNames.mapIndexed { i, name -> PickerOption(name, labels[i]) }.toImmutableList(),
                selectedKey = sort.currentName,
                onSelect = { key -> onEvent(sort.select(key)) },
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
            selectedKey = state.albumFilter.year?.toString() ?: ANY_KEY,
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
            selectedKey = state.albumFilter.genre?.let { GENRE_PREFIX + it } ?: ANY_KEY,
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

/**
 * A tab's sort chip: the current sort, the sorts its picker offers and the event that picks one by
 * name. A tab that gains a sort adds a branch to [tabSort].
 */
private class TabSort<S : Enum<S>>(
    current: S,
    private val options: List<S>,
    label: (S) -> Int,
    private val event: (S) -> LibraryEvent,
) {
    val currentName: String = current.name
    val currentLabel: Int = label(current)
    val optionNames: List<String> = options.map { it.name }
    val optionLabels: List<Int> = options.map(label)

    fun select(name: String): LibraryEvent = event(options.first { it.name == name })
}

/** The sort of the tab on show; null for a tab without one. */
private fun tabSort(state: LibraryUiState): TabSort<*>? = when (state.tab) {
    LibraryTab.Albums ->
        TabSort(state.albumSort, AlbumSortOrder.entries, { it.label }, LibraryEvent::SetAlbumSort)

    LibraryTab.Artists ->
        TabSort(state.artistSort, ArtistSortOrder.entries, { it.label }, LibraryEvent::SetArtistSort)

    LibraryTab.Songs, LibraryTab.Playlists, LibraryTab.Genres, LibraryTab.Downloads -> null
}

internal val AlbumSortOrder.label: Int
    get() = when (this) {
        AlbumSortOrder.Name -> R.string.feature_library_sort_name
        AlbumSortOrder.Artist -> R.string.feature_library_sort_artist
        AlbumSortOrder.RecentlyAdded -> R.string.feature_library_sort_recently_added
        AlbumSortOrder.Year -> R.string.feature_library_sort_year
    }

internal val ArtistSortOrder.label: Int
    get() = when (this) {
        ArtistSortOrder.Name -> R.string.feature_library_sort_name
        ArtistSortOrder.RecentlyAdded -> R.string.feature_library_sort_recently_added
        ArtistSortOrder.AlbumCount -> R.string.feature_library_sort_album_count
    }

private val LibraryMessage.text: Int
    get() = when (this) {
        is LibraryMessage.ActionFailed -> failure.text
        LibraryMessage.RefreshFailed -> R.string.feature_library_error_refresh
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
                albumFilter = AlbumFilter(starred = true),
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
