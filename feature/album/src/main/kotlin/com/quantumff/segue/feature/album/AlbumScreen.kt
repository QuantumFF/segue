package com.quantumff.segue.feature.album

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.art.rememberHeroTint
import com.quantumff.segue.core.designsystem.component.AlbumCarouselSection
import com.quantumff.segue.core.designsystem.component.ContextMenuHeader
import com.quantumff.segue.core.designsystem.component.ContextMenuItem
import com.quantumff.segue.core.designsystem.component.ContextMenuSheet
import com.quantumff.segue.core.designsystem.component.DetailTopBar
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.ErrorState
import com.quantumff.segue.core.designsystem.component.HeroDetailLayout
import com.quantumff.segue.core.designsystem.component.HeroHeaderState
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.designsystem.component.MediaActions
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.Pill
import com.quantumff.segue.core.designsystem.component.PlayButton
import com.quantumff.segue.core.designsystem.component.RatingBar
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SegueSnackbarHost
import com.quantumff.segue.core.designsystem.component.StatusChip
import com.quantumff.segue.core.designsystem.component.TrackRow
import com.quantumff.segue.core.designsystem.component.TrackRowLeading
import com.quantumff.segue.core.designsystem.component.TrackRowSkeleton
import com.quantumff.segue.core.designsystem.component.rememberDelayedVisibility
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toCardState
import com.quantumff.segue.core.designsystem.model.toMenuHeader
import com.quantumff.segue.core.designsystem.model.toTrackRowState
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.tabularNumbers
import com.quantumff.segue.core.designsystem.util.formatDuration
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.DiscTitle
import com.quantumff.segue.core.model.ExplicitStatus
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** Stateful album detail: binds [AlbumViewModel] to [AlbumScreen]. */
@Composable
fun AlbumDetailRoute(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    playActions: PlayActions,
    onAddToPlaylist: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlbumScreen(
        state = state,
        playActions = playActions,
        onBackClick = onBackClick,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onGenreClick = onGenreClick,
        onAddToPlaylist = onAddToPlaylist,
        onPlay = { index, shuffle -> viewModel.play(playActions, index, shuffle) },
        onShuffleAlbums = { viewModel.shuffleAlbums(playActions, it) },
        onToggleStar = viewModel::toggleAlbumStar,
        onToggleSongStar = viewModel::toggleSongStar,
        onRatingChange = viewModel::setRating,
        onRetry = viewModel::refresh,
        onMessageDismiss = viewModel::onMessageDismiss,
        modifier = modifier,
    )
}

/** Album detail (DESIGN.md §4.3): tint hero, actions, rating and genres, disc-grouped tracks, "More by". */
@Suppress("LongParameterList")
@Composable
fun AlbumScreen(
    state: AlbumUiState,
    playActions: PlayActions,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    onPlay: (startIndex: Int, shuffle: Boolean) -> Unit,
    onShuffleAlbums: (ShuffleAlbumsOption) -> Unit,
    onToggleStar: () -> Unit,
    onToggleSongStar: (Song) -> Unit,
    onRatingChange: (Int) -> Unit,
    onRetry: () -> Unit,
    onMessageDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val downloadUnavailable = stringResource(R.string.feature_album_download_unavailable)
    val onDownload: () -> Unit = { scope.launch { snackbarHostState.showSnackbar(downloadUnavailable) } }

    Box(modifier.fillMaxSize()) {
        when (state) {
            AlbumUiState.Loading -> LoadingAlbum(onBackClick)

            AlbumUiState.Error -> MessageWithBackBar(onBackClick) {
                ErrorState(
                    title = stringResource(R.string.feature_album_error_title),
                    message = stringResource(R.string.feature_album_error_message),
                    onRetryClick = onRetry,
                )
            }

            AlbumUiState.NotFound -> MessageWithBackBar(onBackClick) {
                EmptyState(
                    icon = SegueIcons.Disc3,
                    title = stringResource(R.string.feature_album_not_found_title),
                    message = stringResource(R.string.feature_album_not_found_message),
                )
            }

            is AlbumUiState.Success -> AlbumContent(
                state = state,
                playActions = playActions,
                onBackClick = onBackClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onGenreClick = onGenreClick,
                onAddToPlaylist = onAddToPlaylist,
                onPlay = onPlay,
                onShuffleAlbums = onShuffleAlbums,
                onToggleStar = onToggleStar,
                onToggleSongStar = onToggleSongStar,
                onRatingChange = onRatingChange,
                onRetry = onRetry,
                onDownload = onDownload,
            )
        }
        SegueSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    val message = (state as? AlbumUiState.Success)?.message
    val messageText = message?.let {
        stringResource(
            when (it) {
                AlbumMessage.FavoriteFailed -> R.string.feature_album_favorite_failed
                AlbumMessage.RatingFailed -> R.string.feature_album_rating_failed
            },
        )
    }
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
private fun AlbumContent(
    state: AlbumUiState.Success,
    playActions: PlayActions,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    onPlay: (startIndex: Int, shuffle: Boolean) -> Unit,
    onShuffleAlbums: (ShuffleAlbumsOption) -> Unit,
    onToggleStar: () -> Unit,
    onToggleSongStar: (Song) -> Unit,
    onRatingChange: (Int) -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
) {
    val album = state.album
    var albumMenuOpen by rememberSaveable { mutableStateOf(false) }
    var shuffleSheetOpen by rememberSaveable { mutableStateOf(false) }
    var menuSongId by rememberSaveable { mutableStateOf<String?>(null) }

    HeroDetailLayout(
        hero = HeroHeaderState(
            overline = stringResource(R.string.feature_album_overline),
            title = album.name,
            subtitle = album.artist,
            metadata = heroMetadata(album.year, state.trackCount, state.durationSeconds),
            coverArtId = album.coverArtId,
            tint = rememberHeroTint(album.coverArtId),
        ),
        onBackClick = onBackClick,
        onPlayClick = { onPlay(0, false) },
        onMoreClick = { albumMenuOpen = true },
        onSubtitleClick = album.artistId?.let { id -> { onArtistClick(id) } },
        heroActions = {
            PlayButton(onClick = { onPlay(0, false) })
            ShuffleButton(
                onClick = { onPlay(0, true) },
                onLongClick = if (state.shuffleOptions.isEmpty()) null else ({ shuffleSheetOpen = true }),
            )
            SegueIconButton(
                icon = if (album.isStarred) SegueIcons.HeartFilled else SegueIcons.Heart,
                contentDescription = stringResource(
                    if (album.isStarred) R.string.feature_album_unfavorite else R.string.feature_album_favorite,
                ),
                onClick = onToggleStar,
                tint = if (album.isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            SegueIconButton(
                icon = SegueIcons.Download,
                contentDescription = stringResource(R.string.feature_album_download),
                onClick = onDownload,
            )
            SegueIconButton(
                icon = SegueIcons.MoreVertical,
                contentDescription = stringResource(com.quantumff.segue.core.designsystem.R.string.ds_more_options),
                onClick = { albumMenuOpen = true },
            )
        },
    ) {
        item(key = "details", contentType = "details") {
            AlbumDetailsRow(
                rating = album.userRating ?: 0,
                genres = album.genre?.let { persistentListOf(it) } ?: persistentListOf(),
                isOffline = state.isOffline,
                onRatingChange = onRatingChange,
                onGenreClick = onGenreClick,
                onRetry = onRetry,
            )
        }
        trackItems(state, onPlay = { onPlay(it, false) }, onMoreClick = { menuSongId = it.id })
        item(key = "footer", contentType = "footer") {
            AlbumFooter(year = album.year, trackCount = state.trackCount, durationSeconds = state.durationSeconds)
        }
        if (state.moreByArtist.isNotEmpty()) {
            item(key = "moreBy", contentType = "carousel") {
                val cards = remember(state.moreByArtist) {
                    state.moreByArtist.map { it.toCardState(it.year?.toString().orEmpty()) }.toImmutableList()
                }
                AlbumCarouselSection(
                    title = stringResource(R.string.feature_album_more_by, album.artist.orEmpty()),
                    albums = cards,
                    onAlbumClick = { onAlbumClick(it.id) },
                    modifier = Modifier.padding(top = SegueSpacing.sectionGap, bottom = SegueSpacing.lg),
                )
            }
        }
    }

    if (albumMenuOpen) {
        MediaContextMenu(
            header = album.toMenuHeader(),
            actions = MediaActions.album(isStarred = album.isStarred, hasArtist = album.artistId != null),
            onAction = { action ->
                handleAlbumAction(
                    action = action,
                    state = state,
                    playActions = playActions,
                    onPlay = onPlay,
                    onToggleStar = onToggleStar,
                    onArtistClick = onArtistClick,
                    onAddToPlaylist = onAddToPlaylist,
                    onDownload = onDownload,
                )
            },
            onDismissRequest = { albumMenuOpen = false },
        )
    }
    if (shuffleSheetOpen) {
        ShuffleAlbumsSheet(
            album = album,
            options = state.shuffleOptions,
            onOptionClick = onShuffleAlbums,
            onDismissRequest = { shuffleSheetOpen = false },
        )
    }
    state.songs.firstOrNull { it.id == menuSongId }?.let { song ->
        MediaContextMenu(
            header = song.toMenuHeader(),
            actions = MediaActions.song(
                isStarred = song.isStarred,
                hasAlbum = false,
                hasArtist = song.artistId != null,
            ),
            onAction = { action ->
                handleSongAction(
                    action,
                    song,
                    playActions,
                    onToggleSongStar,
                    onArtistClick,
                    onAddToPlaylist,
                    onDownload,
                )
            },
            onDismissRequest = { menuSongId = null },
        )
    }
}

private fun LazyListScope.trackItems(state: AlbumUiState.Success, onPlay: (Int) -> Unit, onMoreClick: (Song) -> Unit) {
    state.discs.forEach { disc ->
        if (state.showDiscHeaders) {
            item(key = "disc-${disc.number}", contentType = "discHeader") {
                DiscHeader(number = disc.number, title = disc.title)
            }
        }
        items(items = disc.tracks, key = { "track-${it.index}-${it.song.id}" }, contentType = { "track" }) { entry ->
            val row = remember(entry.song) { entry.song.toTrackRowState() }
            TrackRow(
                state = row,
                onClick = { onPlay(entry.index) },
                leading = TrackRowLeading.Number,
                onLongClick = { onMoreClick(entry.song) },
                onMoreClick = { onMoreClick(entry.song) },
            )
        }
    }
}

/** "2019 · 12 tracks · 48 min". */
@Composable
private fun heroMetadata(year: Int?, trackCount: Int, durationSeconds: Long): String {
    val minutes = ((durationSeconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE).toInt()
    return listOfNotNull(
        year?.takeIf { it > 0 }?.toString(),
        pluralStringResource(R.plurals.feature_album_tracks, trackCount, trackCount),
        pluralStringResource(R.plurals.feature_album_minutes, minutes, minutes),
    ).joinToString(" · ")
}

@Composable
private fun ShuffleButton(onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(SegueSizes.touchTarget)
            .clip(SegueShapes.full)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.feature_album_shuffle_long_press),
                role = Role.Button,
                interactionSource = null,
                indication = ripple(),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SegueIcons.Shuffle,
            contentDescription = stringResource(R.string.feature_album_shuffle),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(SegueSizes.icon),
        )
    }
}

@Composable
private fun AlbumDetailsRow(
    rating: Int,
    genres: ImmutableList<String>,
    isOffline: Boolean,
    onRatingChange: (Int) -> Unit,
    onGenreClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SegueSpacing.lg)
            .padding(bottom = SegueSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
    ) {
        RatingBar(rating = rating, onRatingChange = onRatingChange)
        if (genres.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm, Alignment.CenterHorizontally)) {
                genres.forEach { genre -> Pill(label = genre, onClick = { onGenreClick(genre) }) }
            }
        }
        if (isOffline) {
            StatusChip(
                label = stringResource(com.quantumff.segue.core.designsystem.R.string.ds_offline_cached),
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun DiscHeader(number: Int, title: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DISC_HEADER_HEIGHT)
            .padding(horizontal = SegueSpacing.lg),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = if (title == null) {
                stringResource(R.string.feature_album_disc, number)
            } else {
                stringResource(R.string.feature_album_disc_titled, number, title)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Released 2019 · 12 tracks, 48:21" (the server gives the year, not the full date). */
@Composable
private fun AlbumFooter(year: Int?, trackCount: Int, durationSeconds: Long) {
    val tracks = pluralStringResource(R.plurals.feature_album_tracks, trackCount, trackCount)
    val duration = formatDuration(durationSeconds)
    Text(
        text = if (year != null && year > 0) {
            stringResource(R.string.feature_album_released, year, tracks, duration)
        } else {
            stringResource(R.string.feature_album_footer, tracks, duration)
        },
        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.lg),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShuffleAlbumsSheet(
    album: Album,
    options: ImmutableList<ShuffleAlbumsOption>,
    onOptionClick: (ShuffleAlbumsOption) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val labels = options.map { option ->
        when (option) {
            is ShuffleAlbumsOption.ByArtist -> stringResource(
                R.string.feature_album_shuffle_by_artist,
                option.artistName,
            )

            is ShuffleAlbumsOption.ByGenre -> stringResource(R.string.feature_album_shuffle_by_genre, option.genre)
        }
    }
    val items = remember(options, labels) {
        options.mapIndexed { index, _ ->
            ContextMenuItem(id = index.toString(), label = labels[index], icon = SegueIcons.ShuffleAlbums)
        }.toImmutableList()
    }
    ContextMenuSheet(
        header = ContextMenuHeader(
            title = stringResource(R.string.feature_album_shuffle_albums_title),
            subtitle = album.name,
            coverArtId = album.coverArtId,
        ),
        items = items,
        onItemClick = { item ->
            onOptionClick(options[item.id.toInt()])
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun LoadingAlbum(onBackClick: () -> Unit) {
    val showSkeleton = rememberDelayedVisibility()
    MessageWithBackBar(onBackClick) {
        if (showSkeleton) {
            Column(Modifier.fillMaxSize().padding(top = SKELETON_TOP)) {
                repeat(SKELETON_ROWS) { TrackRowSkeleton() }
            }
        }
    }
}

@Composable
private fun MessageWithBackBar(onBackClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        DetailTopBar(title = "", collapsed = false, onBackClick = onBackClick)
    }
}

/** Album ⋮ menu actions (DESIGN.md §5.7). */
@Suppress("LongParameterList")
internal fun handleAlbumAction(
    action: MediaAction,
    state: AlbumUiState.Success,
    playActions: PlayActions,
    onPlay: (startIndex: Int, shuffle: Boolean) -> Unit,
    onToggleStar: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    onDownload: () -> Unit,
) {
    val artistId = state.album.artistId
    when (action) {
        MediaAction.Play -> onPlay(0, false)

        MediaAction.Shuffle -> onPlay(0, true)

        MediaAction.ShuffleArtistAlbums, MediaAction.ShuffleAlbums ->
            artistId?.let { playActions.onShuffleAlbums(AlbumShuffleSource.Artist(it)) }

        MediaAction.PlayNext -> if (state.songs.isNotEmpty()) playActions.onPlayNext(state.songs)

        MediaAction.AddToQueue -> if (state.songs.isNotEmpty()) playActions.onAddToQueue(state.songs)

        MediaAction.AddToPlaylist -> if (state.songs.isNotEmpty()) onAddToPlaylist(state.songs.map { it.id })

        MediaAction.Favorite, MediaAction.Unfavorite -> onToggleStar()

        MediaAction.GoToArtist -> artistId?.let(onArtistClick)

        MediaAction.Download -> onDownload()

        else -> Unit
    }
}

/** Track ⋮ menu actions (DESIGN.md §5.7). */
@Suppress("LongParameterList")
internal fun handleSongAction(
    action: MediaAction,
    song: Song,
    playActions: PlayActions,
    onToggleSongStar: (Song) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
    onDownload: () -> Unit,
) {
    when (action) {
        MediaAction.PlayNext -> playActions.onPlayNext(listOf(song))
        MediaAction.AddToQueue -> playActions.onAddToQueue(listOf(song))
        MediaAction.InstantMix -> playActions.onStartInstantMix(song)
        MediaAction.AddToPlaylist -> onAddToPlaylist(listOf(song.id))
        MediaAction.Favorite, MediaAction.Unfavorite -> onToggleSongStar(song)
        MediaAction.GoToArtist -> song.artistId?.let(onArtistClick)
        MediaAction.Download -> onDownload()
        else -> Unit
    }
}

private const val SECONDS_PER_MINUTE = 60L
private const val SKELETON_ROWS = 8
private val SKELETON_TOP = SegueSizes.topBar * 2
private val DISC_HEADER_HEIGHT = SegueSizes.touchTarget - SegueSpacing.sm

internal val PreviewAlbumState = run {
    val album = Album(
        id = "al-1",
        name = "In Rainbows",
        artist = "Radiohead",
        artistId = "ar-1",
        artists = persistentListOf(),
        coverArtId = null,
        songCount = 5,
        durationSeconds = 1290,
        year = 2007,
        genre = "Alternative",
        createdAt = null,
        starredAt = 1L,
        userRating = 4,
        playCount = null,
        playedAt = null,
        isCompilation = false,
        discTitles = persistentListOf(DiscTitle(2, "Bonus")),
        musicBrainzId = null,
        explicitStatus = ExplicitStatus.None,
    )
    val titles = listOf("15 Step", "Bodysnatchers", "Nude", "MK 1", "Down Is the New Up")
    val songs = titles.mapIndexed { index, title ->
        Song(
            id = "s$index",
            title = title,
            albumId = album.id,
            album = album.name,
            artist = "Radiohead",
            artistId = "ar-1",
            track = if (index < 3) index + 1 else index - 2,
            discNumber = if (index < 3) 1 else 2,
            durationSeconds = 240 + index * 13,
        )
    }.toImmutableList()
    AlbumUiState.Success(
        album = album,
        songs = songs,
        discs = groupDiscs(album, songs),
        moreByArtist = persistentListOf(
            album.copy(id = "al-2", name = "OK Computer", year = 1997),
            album.copy(id = "al-3", name = "Kid A", year = 2000),
        ),
    )
}

@PreviewSegueThemes
@Composable
private fun AlbumScreenPreview() {
    SeguePreview {
        AlbumScreen(
            state = PreviewAlbumState,
            playActions = PlayActions.None,
            onBackClick = {},
            onAlbumClick = {},
            onArtistClick = {},
            onGenreClick = {},
            onAddToPlaylist = {},
            onPlay = { _, _ -> },
            onShuffleAlbums = {},
            onToggleStar = {},
            onToggleSongStar = {},
            onRatingChange = {},
            onRetry = {},
            onMessageDismiss = {},
        )
    }
}

@PreviewSegueThemes
@Composable
private fun AlbumScreenErrorPreview() {
    SeguePreview {
        AlbumScreen(
            state = AlbumUiState.Error,
            playActions = PlayActions.None,
            onBackClick = {},
            onAlbumClick = {},
            onArtistClick = {},
            onGenreClick = {},
            onAddToPlaylist = {},
            onPlay = { _, _ -> },
            onShuffleAlbums = {},
            onToggleStar = {},
            onToggleSongStar = {},
            onRatingChange = {},
            onRetry = {},
            onMessageDismiss = {},
        )
    }
}
