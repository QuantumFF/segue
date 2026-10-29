package com.quantumff.segue.feature.artist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.data.play.MediaActionHost
import com.quantumff.segue.core.designsystem.R as DesignR
import com.quantumff.segue.core.designsystem.art.rememberHeroTint
import com.quantumff.segue.core.designsystem.component.AlbumCarouselSection
import com.quantumff.segue.core.designsystem.component.ArtistCard
import com.quantumff.segue.core.designsystem.component.DetailTopBar
import com.quantumff.segue.core.designsystem.component.DownloadButton
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.ErrorState
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtons
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtonsInset
import com.quantumff.segue.core.designsystem.component.HeroDetailLayout
import com.quantumff.segue.core.designsystem.component.HeroHeaderState
import com.quantumff.segue.core.designsystem.component.HorizontalCarousel
import com.quantumff.segue.core.designsystem.component.InlineSpinner
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.PlayButton
import com.quantumff.segue.core.designsystem.component.SectionHeader
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.StatusChip
import com.quantumff.segue.core.designsystem.component.TrackRow
import com.quantumff.segue.core.designsystem.component.TrackRowSkeleton
import com.quantumff.segue.core.designsystem.component.TrackSwipeBox
import com.quantumff.segue.core.designsystem.component.rememberCollapseOnScrollState
import com.quantumff.segue.core.designsystem.component.rememberDelayedVisibility
import com.quantumff.segue.core.designsystem.component.text
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toCardState
import com.quantumff.segue.core.designsystem.model.toMenuHeader
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ExplicitStatus
import com.quantumff.segue.core.model.HeroPlayback
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActions
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Stateful artist detail: binds [ArtistViewModel] to [ArtistScreen]. */
@Composable
fun ArtistDetailRoute(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val menuHost = remember(onAlbumClick, onArtistClick, onAddToPlaylist) {
        MediaActionHost(onAlbumClick = onAlbumClick, onArtistClick = onArtistClick, onAddToPlaylist = onAddToPlaylist)
    }
    ArtistScreen(
        state = state,
        onBackClick = onBackClick,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onPlay = viewModel::play,
        onHeroPlay = viewModel::onHeroPlay,
        onPlayTopSong = viewModel::playTopSong,
        onShuffleAlbums = viewModel::shuffleAlbums,
        onPlayAlbums = viewModel::playAlbums,
        onInstantMix = viewModel::startInstantMix,
        onToggleStar = viewModel::toggleArtistStar,
        onRetry = viewModel::refresh,
        onMessageDismiss = viewModel::onMessageDismiss,
        modifier = modifier,
        onDownload = viewModel::download,
        onRemoveDownload = viewModel::removeDownload,
        onArtistAction = { viewModel.onArtistAction(it, menuHost) },
        onSongAction = { song, action -> viewModel.onSongAction(song, action, menuHost) },
        onSongSwipe = viewModel::onSongSwipe,
    )
}

/**
 * Artist detail (DESIGN.md §4.4): circular-art hero, actions, then top songs, albums, appears on,
 * similar artists and the biography.
 */
@Suppress("LongParameterList")
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlay: (shuffle: Boolean) -> Unit,
    onHeroPlay: () -> Unit,
    onPlayTopSong: (index: Int) -> Unit,
    onShuffleAlbums: () -> Unit,
    onInstantMix: () -> Unit,
    onToggleStar: () -> Unit,
    onRetry: () -> Unit,
    onMessageDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayAlbums: (AlbumOrder, QueuePlacement) -> Unit = { _, _ -> },
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {},
    onArtistAction: (MediaAction) -> Unit = {},
    onSongAction: (Song, MediaAction) -> Unit = { _, _ -> },
    onSongSwipe: (Song, QueuePlacement) -> Unit = { _, _ -> },
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Box(modifier.fillMaxSize()) {
        when (state) {
            ArtistUiState.Loading -> {
                val showSkeleton = rememberDelayedVisibility()
                WithBackBar(onBackClick) {
                    if (showSkeleton) {
                        Column(Modifier.fillMaxSize().padding(top = SegueSizes.topBar * 2)) {
                            repeat(SKELETON_ROWS) { TrackRowSkeleton() }
                        }
                    }
                }
            }

            ArtistUiState.Error -> WithBackBar(onBackClick) {
                ErrorState(
                    title = stringResource(R.string.feature_artist_error_title),
                    message = stringResource(R.string.feature_artist_error_message),
                    onRetryClick = onRetry,
                )
            }

            ArtistUiState.NotFound -> WithBackBar(onBackClick) {
                EmptyState(
                    icon = SegueIcons.Artist,
                    title = stringResource(R.string.feature_artist_not_found_title),
                    message = stringResource(R.string.feature_artist_not_found_message),
                )
            }

            is ArtistUiState.Success -> ArtistContent(
                state = state,
                onBackClick = onBackClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlay = onPlay,
                onHeroPlay = onHeroPlay,
                onPlayTopSong = onPlayTopSong,
                onShuffleAlbums = onShuffleAlbums,
                onPlayAlbums = onPlayAlbums,
                onInstantMix = onInstantMix,
                onToggleStar = onToggleStar,
                onRetry = onRetry,
                onDownload = onDownload,
                onRemoveDownload = onRemoveDownload,
                onArtistAction = onArtistAction,
                onSongAction = onSongAction,
                onSongSwipe = onSongSwipe,
            )
        }
        SegueSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    val message = (state as? ArtistUiState.Success)?.message
    val messageText = message?.let { stringResource(it.text) }
    val currentOnMessageDismiss by rememberUpdatedState(onMessageDismiss)
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            currentOnMessageDismiss()
        }
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun ArtistContent(
    state: ArtistUiState.Success,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlay: (shuffle: Boolean) -> Unit,
    onHeroPlay: () -> Unit,
    onPlayTopSong: (index: Int) -> Unit,
    onShuffleAlbums: () -> Unit,
    onPlayAlbums: (AlbumOrder, QueuePlacement) -> Unit,
    onInstantMix: () -> Unit,
    onToggleStar: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onArtistAction: (MediaAction) -> Unit,
    onSongAction: (Song, MediaAction) -> Unit,
    onSongSwipe: (Song, QueuePlacement) -> Unit,
) {
    val artist = state.artist
    var artistMenuOpen by rememberSaveable { mutableStateOf(false) }
    var downloadMenuOpen by rememberSaveable { mutableStateOf(false) }
    var menuSongId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllTopSongs by rememberSaveable { mutableStateOf(false) }
    var biographyExpanded by rememberSaveable { mutableStateOf(false) }
    val collapse = rememberCollapseOnScrollState()
    val hasAlbums = state.albums.isNotEmpty() || state.appearsOn.isNotEmpty()

    HeroDetailLayout(
        modifier = Modifier.nestedScroll(collapse.connection),
        extraBottomPadding = if (hasAlbums) FloatingAlbumPlayButtonsInset else 0.dp,
        // The artist's albums in the order the page lists them (DESIGN.md §5.9).
        overlay = {
            FloatingAlbumPlayButtons(visible = hasAlbums, expanded = collapse.expanded, onPlay = onPlayAlbums)
        },
        hero = HeroHeaderState(
            overline = stringResource(R.string.feature_artist_overline),
            title = artist.name,
            subtitle = null,
            metadata = listOf(
                pluralStringResource(R.plurals.feature_artist_albums, state.albumCount, state.albumCount),
                pluralStringResource(R.plurals.feature_artist_tracks, state.trackCount, state.trackCount),
            ).joinToString(" · "),
            coverArtId = artist.coverArtId,
            tint = rememberHeroTint(artist.coverArtId),
            circularArt = true,
        ),
        onBackClick = onBackClick,
        onPlayClick = onHeroPlay,
        isPlaying = state.heroPlayback == HeroPlayback.Playing,
        onMoreClick = { artistMenuOpen = true },
        heroActions = {
            PlayButton(onClick = onHeroPlay, isPlaying = state.heroPlayback == HeroPlayback.Playing)
            SegueIconButton(
                icon = SegueIcons.Shuffle,
                contentDescription = stringResource(R.string.feature_artist_shuffle),
                onClick = { onPlay(true) },
            )
            SegueIconButton(
                icon = SegueIcons.ShuffleAlbums,
                contentDescription = stringResource(R.string.feature_artist_shuffle_albums),
                onClick = onShuffleAlbums,
            )
            SegueIconButton(
                icon = SegueIcons.Related,
                contentDescription = stringResource(R.string.feature_artist_instant_mix),
                onClick = onInstantMix,
                enabled = !state.isPreparingPlayback,
            )
            SegueIconButton(
                icon = if (artist.isStarred) SegueIcons.HeartFilled else SegueIcons.Heart,
                contentDescription = stringResource(
                    if (artist.isStarred) R.string.feature_artist_unfavorite else R.string.feature_artist_favorite,
                ),
                onClick = onToggleStar,
                tint = if (artist.isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            DownloadButton(
                status = state.download,
                onClick = { if (state.download == null) onDownload() else downloadMenuOpen = true },
            )
        },
    ) {
        if (state.isOffline || state.isPreparingPlayback) {
            item(key = "status", contentType = "status") {
                Box(Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.xs)) {
                    if (state.isPreparingPlayback) {
                        InlineSpinner()
                    } else {
                        StatusChip(label = stringResource(DesignR.string.ds_offline_cached), onClick = onRetry)
                    }
                }
            }
        }
        if (state.topSongs.isNotEmpty()) {
            item(key = "topSongsHeader", contentType = "sectionHeader") {
                SectionHeader(
                    title = stringResource(R.string.feature_artist_top_songs),
                    onSeeAllClick = if (!showAllTopSongs && state.topSongs.size > TOP_SONGS_PREVIEW) {
                        { showAllTopSongs = true }
                    } else {
                        null
                    },
                )
            }
            val shown = if (showAllTopSongs) state.topSongs else state.topSongs.take(TOP_SONGS_PREVIEW)
            itemsIndexed(shown, key = { index, song -> "top-$index-${song.id}" }, contentType = { _, _ -> "track" }) {
                    index,
                    song,
                ->
                val row = remember(song, state.availability, state.currentSong) { state.trackRow(song) }
                TrackSwipeBox(
                    onPlayNext = { onSongSwipe(song, QueuePlacement.Next) },
                    onAddToQueue = { onSongSwipe(song, QueuePlacement.End) },
                    enabled = row.isAvailable,
                ) {
                    TrackRow(
                        state = row,
                        onClick = { onPlayTopSong(index) },
                        onLongClick = { menuSongId = song.id },
                        onMoreClick = { menuSongId = song.id },
                    )
                }
            }
            if (showAllTopSongs) {
                item(key = "topSongsLess", contentType = "button") {
                    TextButton(
                        onClick = { showAllTopSongs = false },
                        modifier = Modifier.padding(horizontal = SegueSpacing.sm),
                    ) { Text(stringResource(R.string.feature_artist_show_less)) }
                }
            }
        }
        albumSection("albums", R.string.feature_artist_section_albums, state.albums, onAlbumClick)
        albumSection("appearsOn", R.string.feature_artist_section_appears_on, state.appearsOn, onAlbumClick)
        if (state.similarArtists.isNotEmpty()) {
            item(key = "similar", contentType = "artistCarousel") {
                SimilarArtists(state.similarArtists, onArtistClick)
            }
        }
        state.biography?.let { biography ->
            item(key = "about", contentType = "about") {
                About(
                    biography = biography,
                    expanded = biographyExpanded,
                    onToggle = { biographyExpanded = !biographyExpanded },
                )
            }
        }
    }

    if (artistMenuOpen) {
        MediaContextMenu(
            header = artist.toMenuHeader(),
            actions = MediaActions.artist(isStarred = artist.isStarred, isDownloaded = state.download != null),
            onAction = onArtistAction,
            onDismissRequest = { artistMenuOpen = false },
        )
    }
    if (downloadMenuOpen) {
        MediaContextMenu(
            header = artist.toMenuHeader(),
            actions = persistentListOf(MediaAction.RemoveDownload),
            onAction = { onRemoveDownload() },
            onDismissRequest = { downloadMenuOpen = false },
        )
    }
    state.topSongs.firstOrNull { it.id == menuSongId }?.let { song ->
        MediaContextMenu(
            header = song.toMenuHeader(),
            actions = MediaActions.song(
                isStarred = song.isStarred,
                hasAlbum = song.albumId != null,
                hasArtist = song.artistId != null && song.artistId != artist.id,
            ),
            onAction = { onSongAction(song, it) },
            onDismissRequest = { menuSongId = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.albumSection(
    key: String,
    title: Int,
    albums: ImmutableList<Album>,
    onAlbumClick: (String) -> Unit,
) {
    if (albums.isEmpty()) return
    item(key = key, contentType = "albumCarousel") {
        val cards = remember(albums) { albums.map { it.toCardState(it.year?.toString().orEmpty()) }.toImmutableList() }
        AlbumCarouselSection(
            title = stringResource(title),
            albums = cards,
            onAlbumClick = { onAlbumClick(it.id) },
            modifier = Modifier.padding(top = SegueSpacing.lg),
        )
    }
}

@Composable
private fun SimilarArtists(artists: ImmutableList<Artist>, onArtistClick: (String) -> Unit) {
    val cards = remember(artists) { artists.map { it.toCardState(subtitle = null) }.toImmutableList() }
    Column(Modifier.padding(top = SegueSpacing.lg)) {
        SectionHeader(title = stringResource(R.string.feature_artist_section_similar))
        HorizontalCarousel(
            items = cards,
            key = { it.id },
            itemWidth = SegueSizes.artistCircle,
            contentType = "artistCard",
        ) { card ->
            ArtistCard(
                state = card,
                onClick = { onArtistClick(card.id) },
                modifier = Modifier.width(SegueSizes.artistCircle),
            )
        }
    }
}

@Composable
private fun About(biography: String, expanded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.padding(top = SegueSpacing.lg, bottom = SegueSpacing.lg)) {
        SectionHeader(title = stringResource(R.string.feature_artist_section_about))
        Text(
            text = biography,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else BIOGRAPHY_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = SegueSpacing.lg),
        )
        TextButton(onClick = onToggle, modifier = Modifier.padding(horizontal = SegueSpacing.sm)) {
            Text(stringResource(if (expanded) R.string.feature_artist_less else R.string.feature_artist_more))
        }
    }
}

@Composable
private fun WithBackBar(onBackClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        DetailTopBar(title = "", collapsed = false, onBackClick = onBackClick)
    }
}

private const val TOP_SONGS_PREVIEW = 5
private const val BIOGRAPHY_LINES = 3
private const val SKELETON_ROWS = 8

private val PreviewArtistState = run {
    val artist = Artist("ar-1", "Radiohead", albumCount = 9, coverArtId = null, starredAt = null, null, null)
    fun album(id: String, name: String, year: Int, artistId: String = "ar-1") = Album(
        id = id, name = name, artist = "Radiohead", artistId = artistId, artists = persistentListOf(),
        coverArtId = null, songCount = 10, durationSeconds = 2600, year = year, genre = null, createdAt = null,
        starredAt = null, userRating = null, playCount = null, playedAt = null, isCompilation = false,
        discTitles = persistentListOf(), musicBrainzId = null, explicitStatus = ExplicitStatus.None,
    )
    ArtistUiState.Success(
        artist = artist,
        albums = persistentListOf(album("a1", "In Rainbows", 2007), album("a2", "Kid A", 2000)),
        appearsOn = persistentListOf(album("a3", "Help!", 1995, artistId = "va")),
        topSongs = listOf("Creep", "Karma Police", "No Surprises", "Reckoner", "Everything in Its Right Place")
            .mapIndexed { index, title ->
                Song(
                    id = "s$index",
                    title = title,
                    albumId = "a1",
                    album = "OK Computer",
                    artist = "Radiohead",
                    artistId = "ar-1",
                    durationSeconds = 230 + index * 11,
                )
            }.toImmutableList(),
        similarArtists = persistentListOf(artist.copy(id = "ar-2", name = "Portishead")),
        biography = "Radiohead are an English rock band formed in Abingdon, Oxfordshire, in 1985.",
        trackCount = 30,
    )
}

@PreviewSegueThemes
@Composable
private fun ArtistScreenPreview() {
    SeguePreview {
        ArtistScreen(
            state = PreviewArtistState,
            onBackClick = {},
            onAlbumClick = {},
            onArtistClick = {},
            onPlay = {},
            onHeroPlay = {},
            onPlayTopSong = {},
            onShuffleAlbums = {},
            onInstantMix = {},
            onToggleStar = {},
            onRetry = {},
            onMessageDismiss = {},
        )
    }
}
