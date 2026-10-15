package com.quantumff.segue.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.component.AlbumCard
import com.quantumff.segue.core.designsystem.component.ArtistCard
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.HorizontalCarousel
import com.quantumff.segue.core.designsystem.component.InlineSpinner
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.designsystem.component.MediaActions
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.MediaRow
import com.quantumff.segue.core.designsystem.component.Pill
import com.quantumff.segue.core.designsystem.component.SearchTopBar
import com.quantumff.segue.core.designsystem.component.SectionHeader
import com.quantumff.segue.core.designsystem.component.SegueFilterChip
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.StatusChip
import com.quantumff.segue.core.designsystem.component.TrackRow
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toCardState
import com.quantumff.segue.core.designsystem.model.toMenuHeader
import com.quantumff.segue.core.designsystem.model.toTrackRowState
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.model.Song
import kotlinx.collections.immutable.persistentListOf

/** Search tab (DESIGN.md §4.6). Stateful wrapper: wires the ViewModel and routes play intents. */
@Composable
fun SearchScreen(
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    playActions: PlayActions,
    onAddToPlaylist: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val songs by rememberUpdatedState(uiState.songs)
    val callbacks = remember(viewModel, playActions, onAlbumClick, onArtistClick, onGenreClick, onAddToPlaylist) {
        SearchCallbacks(
            onQueryChange = viewModel::onQueryChange,
            onSubmit = viewModel::onSubmit,
            onFilterChange = viewModel::onFilterChange,
            onRecentSearchClick = viewModel::onRecentSearchClick,
            onRemoveRecentSearch = viewModel::onRemoveRecentSearch,
            onGenreClick = onGenreClick,
            onAlbumClick = { id ->
                viewModel.onResultOpened()
                onAlbumClick(id)
            },
            onArtistClick = { id ->
                viewModel.onResultOpened()
                onArtistClick(id)
            },
            onSongClick = { index ->
                viewModel.onResultOpened()
                playActions.onPlaySongs(songs, index, false)
            },
            onSongAction = { song, action ->
                when (action) {
                    MediaAction.PlayNext -> playActions.onPlayNext(listOf(song))

                    MediaAction.AddToQueue -> playActions.onAddToQueue(listOf(song))

                    MediaAction.InstantMix -> playActions.onStartInstantMix(song.id)

                    MediaAction.AddToPlaylist -> onAddToPlaylist(listOf(song.id))

                    MediaAction.Favorite, MediaAction.Unfavorite -> viewModel.onToggleFavorite(song)

                    MediaAction.GoToAlbum -> song.albumId?.let(onAlbumClick)

                    MediaAction.GoToArtist -> song.artistId?.let(onArtistClick)

                    // TODO(downloads): pin the song for offline once downloads exist.
                    else -> Unit
                }
            },
            onMessageDismiss = viewModel::onMessageDismiss,
        )
    }
    SearchContent(
        query = query,
        uiState = uiState,
        callbacks = callbacks,
        modifier = modifier,
    )
}

/** Every user event the search screen raises, so the stateless screen stays a two-argument call. */
class SearchCallbacks(
    val onQueryChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onFilterChange: (SearchFilter) -> Unit,
    val onRecentSearchClick: (String) -> Unit,
    val onRemoveRecentSearch: (String) -> Unit,
    val onGenreClick: (String) -> Unit,
    val onAlbumClick: (String) -> Unit,
    val onArtistClick: (String) -> Unit,
    /** Index into [SearchUiState.songs]. */
    val onSongClick: (Int) -> Unit,
    val onSongAction: (Song, MediaAction) -> Unit,
    val onMessageDismiss: () -> Unit,
) {
    companion object {
        val None = SearchCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {})
    }
}

/** The stateless search screen. */
@Composable
fun SearchContent(
    query: String,
    uiState: SearchUiState,
    callbacks: SearchCallbacks,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    var menuSong by remember { mutableStateOf<Song?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val favoriteFailed = stringResource(R.string.feature_search_favorite_failed)
    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            snackbarHostState.showSnackbar(favoriteFailed)
            callbacks.onMessageDismiss()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SearchTopBar(
                query = query,
                onQueryChange = callbacks.onQueryChange,
                autoFocus = autoFocus,
                onSearch = callbacks.onSubmit,
            )
            when (val results = uiState.results) {
                SearchResults.Idle -> BrowseContent(uiState, callbacks)

                SearchResults.Loading -> Box(Modifier.fillMaxWidth().padding(SegueSpacing.xl), Alignment.Center) {
                    InlineSpinner()
                }

                is SearchResults.Ready -> ResultsContent(
                    query = query.trim(),
                    results = results,
                    filter = uiState.filter,
                    callbacks = callbacks,
                    onSongMoreClick = { menuSong = it },
                )
            }
        }
        SegueSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    menuSong?.let { song ->
        MediaContextMenu(
            header = song.toMenuHeader(),
            actions = MediaActions.song(
                isStarred = song.isStarred,
                hasAlbum = song.albumId != null,
                hasArtist = song.artistId != null,
            ),
            onAction = { callbacks.onSongAction(song, it) },
            onDismissRequest = { menuSong = null },
        )
    }
}

@Composable
private fun BrowseContent(uiState: SearchUiState, callbacks: SearchCallbacks) {
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
        if (uiState.recentSearches.isNotEmpty()) {
            item(key = "recentHeader", contentType = "header") {
                SectionHeader(title = stringResource(R.string.feature_search_recent))
            }
            items(uiState.recentSearches, key = { "recent:$it" }, contentType = { "recent" }) { recent ->
                RecentSearchRow(
                    query = recent,
                    onClick = { callbacks.onRecentSearchClick(recent) },
                    onDismiss = { callbacks.onRemoveRecentSearch(recent) },
                )
            }
        }
        if (uiState.genres.isNotEmpty()) {
            item(key = "genresHeader", contentType = "header") {
                SectionHeader(title = stringResource(R.string.feature_search_browse_genres))
            }
            item(key = "genres", contentType = "genres") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
                    modifier = Modifier.padding(horizontal = SegueSpacing.lg),
                ) {
                    uiState.genres.forEach { genre ->
                        Pill(label = genre.name, onClick = { callbacks.onGenreClick(genre.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchRow(query: String, onClick: () -> Unit, onDismiss: () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        onDismiss = { onDismiss() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = SegueSpacing.lg),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(SegueIcons.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SegueSizes.sheetRow)
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onClick)
                .padding(horizontal = SegueSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = SegueIcons.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SegueSizes.iconSmall),
            )
            Spacer(Modifier.width(SegueSpacing.lg))
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResultsContent(
    query: String,
    results: SearchResults.Ready,
    filter: SearchFilter,
    callbacks: SearchCallbacks,
    onSongMoreClick: (Song) -> Unit,
) {
    val result = results.result
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        FilterRow(filter = filter, results = results, onFilterChange = callbacks.onFilterChange)
        if (result.isEmpty()) {
            EmptyState(
                icon = SegueIcons.Search,
                title = stringResource(R.string.feature_search_no_results, query),
                message = if (results.offline) stringResource(R.string.feature_search_no_results_offline) else null,
                modifier = Modifier.weight(1f),
            )
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
            when (filter) {
                SearchFilter.All -> allResults(result, callbacks, onSongMoreClick)
                SearchFilter.Songs -> songRows(result.songs, callbacks, onSongMoreClick)
                SearchFilter.Albums -> albumRows(result.albums, callbacks.onAlbumClick)
                SearchFilter.Artists -> artistRows(result.artists, callbacks.onArtistClick)
            }
        }
    }
}

@Composable
private fun FilterRow(filter: SearchFilter, results: SearchResults.Ready, onFilterChange: (SearchFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SegueSizes.touchTarget)
            .padding(end = SegueSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(start = SegueSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
        ) {
            SearchFilter.entries.forEach { value ->
                SegueFilterChip(
                    label = stringResource(value.label),
                    selected = value == filter,
                    onClick = { onFilterChange(value) },
                )
            }
        }
        when {
            results.serverPending -> {
                Spacer(Modifier.width(SegueSpacing.sm))
                InlineSpinner()
            }

            results.offline && !results.result.isEmpty() -> {
                Spacer(Modifier.width(SegueSpacing.sm))
                StatusChip(label = stringResource(R.string.feature_search_offline_results))
            }
        }
    }
}

private fun LazyListScope.allResults(
    result: SearchResult,
    callbacks: SearchCallbacks,
    onSongMoreClick: (Song) -> Unit,
) {
    if (result.artists.isNotEmpty()) {
        item(key = "artistsHeader", contentType = "header") {
            SectionHeader(title = stringResource(R.string.feature_search_artists))
        }
        item(key = "artists", contentType = "artistRow") {
            HorizontalCarousel(
                items = result.artists,
                key = { it.id },
                itemWidth = ARTIST_CIRCLE,
                contentType = "artistCard",
            ) { artist ->
                ArtistCard(
                    state = artist.toCardState(subtitle = null),
                    onClick = { callbacks.onArtistClick(artist.id) },
                    artDisplaySize = ARTIST_CIRCLE,
                    modifier = Modifier.width(ARTIST_CIRCLE),
                )
            }
        }
    }
    if (result.albums.isNotEmpty()) {
        item(key = "albumsHeader", contentType = "header") {
            SectionHeader(title = stringResource(R.string.feature_search_albums))
        }
        item(key = "albums", contentType = "albumRow") {
            HorizontalCarousel(
                items = result.albums,
                key = { it.id },
                itemWidth = ALBUM_CARD,
                contentType = "albumCard",
            ) { album ->
                AlbumCard(
                    state = album.toCardState(),
                    onClick = { callbacks.onAlbumClick(album.id) },
                    artDisplaySize = ALBUM_CARD,
                    modifier = Modifier.width(ALBUM_CARD),
                )
            }
        }
    }
    if (result.songs.isNotEmpty()) {
        item(key = "songsHeader", contentType = "header") {
            SectionHeader(
                title = stringResource(R.string.feature_search_songs),
                onSeeAllClick = if (result.songs.size > ALL_SONG_LIMIT) {
                    { callbacks.onFilterChange(SearchFilter.Songs) }
                } else {
                    null
                },
            )
        }
        songRows(result.songs.take(ALL_SONG_LIMIT), callbacks, onSongMoreClick)
    }
}

private fun LazyListScope.songRows(songs: List<Song>, callbacks: SearchCallbacks, onSongMoreClick: (Song) -> Unit) {
    itemsIndexed(songs, key = { _, song -> "song:${song.id}" }, contentType = { _, _ -> "song" }) { index, song ->
        TrackRow(
            state = song.toTrackRowState(),
            onClick = { callbacks.onSongClick(index) },
            onLongClick = { onSongMoreClick(song) },
            onMoreClick = { onSongMoreClick(song) },
        )
    }
}

private fun LazyListScope.albumRows(albums: List<Album>, onAlbumClick: (String) -> Unit) {
    items(albums, key = { "album:${it.id}" }, contentType = { "album" }) { album ->
        MediaRow(
            title = album.name,
            subtitle = album.artist,
            coverArtId = album.coverArtId,
            onClick = { onAlbumClick(album.id) },
        )
    }
}

private fun LazyListScope.artistRows(artists: List<Artist>, onArtistClick: (String) -> Unit) {
    items(artists, key = { "artist:${it.id}" }, contentType = { "artist" }) { artist ->
        MediaRow(
            title = artist.name,
            subtitle = null,
            coverArtId = artist.coverArtId,
            onClick = { onArtistClick(artist.id) },
            artShape = SegueShapes.full,
            placeholderIcon = SegueIcons.Artist,
        )
    }
}

private fun SearchResult.isEmpty() = artists.isEmpty() && albums.isEmpty() && songs.isEmpty()

private val SearchFilter.label: Int
    get() = when (this) {
        SearchFilter.All -> R.string.feature_search_filter_all
        SearchFilter.Songs -> R.string.feature_search_songs
        SearchFilter.Albums -> R.string.feature_search_albums
        SearchFilter.Artists -> R.string.feature_search_artists
    }

/** "All" shows up to this many songs, then "See all" (DESIGN.md §4.6). */
private const val ALL_SONG_LIMIT = 20
private val ARTIST_CIRCLE = 72.dp
private val ALBUM_CARD = 128.dp

@PreviewSegueThemes
@Composable
private fun SearchBrowsePreview() {
    SeguePreview {
        SearchContent(
            query = "",
            uiState = SearchUiState(
                recentSearches = persistentListOf("radiohead", "in rainbows"),
                genres = PreviewSearch.genres,
            ),
            callbacks = SearchCallbacks.None,
            autoFocus = false,
        )
    }
}

@PreviewSegueThemes
@Composable
private fun SearchResultsPreview() {
    SeguePreview {
        SearchContent(
            query = "radio",
            uiState = SearchUiState(
                results = SearchResults.Ready(PreviewSearch.result, serverPending = false, offline = true),
            ),
            callbacks = SearchCallbacks.None,
            autoFocus = false,
        )
    }
}

@PreviewSegueThemes
@Composable
private fun SearchEmptyPreview() {
    SeguePreview {
        SearchContent(
            query = "zzz",
            uiState = SearchUiState(
                results = SearchResults.Ready(SearchResult.Empty, serverPending = false, offline = false),
            ),
            callbacks = SearchCallbacks.None,
            autoFocus = false,
        )
    }
}
