package com.quantumff.segue.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.PlayActions
import java.text.NumberFormat
import kotlinx.coroutines.flow.flowOf

/** One genre's albums: a secondary top bar with "Shuffle albums", then the album grid. */
@Composable
fun GenreScreen(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    playActions: PlayActions,
    modifier: Modifier = Modifier,
    viewModel: GenreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GenreContent(
        state = state,
        albums = viewModel.albums.collectAsLazyPagingItems(),
        onBackClick = onBackClick,
        onAlbumClick = onAlbumClick,
        onShuffleAlbumsClick = { playActions.onShuffleAlbums(AlbumShuffleSource.Genre(state.genre)) },
        modifier = modifier,
    )
}

@Composable
internal fun GenreContent(
    state: GenreUiState,
    albums: LazyPagingItems<Album>,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onShuffleAlbumsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = state.albumCount?.let {
        pluralStringResource(R.plurals.feature_library_album_count, it, NumberFormat.getIntegerInstance().format(it))
    }
    Column(modifier.fillMaxSize()) {
        SubScreenTopBar(title = state.genre, subtitle = count, onBackClick = onBackClick) {
            SegueIconButton(
                icon = SegueIcons.ShuffleAlbums,
                contentDescription = stringResource(R.string.feature_library_shuffle_albums),
                onClick = onShuffleAlbumsClick,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val bottom = LocalShellContentPadding.current.calculateBottomPadding()
        PagedAlbumGrid(
            albums = albums,
            onAlbumClick = { onAlbumClick(it.id) },
            contentPadding = PaddingValues(
                start = SegueSpacing.lg,
                end = SegueSpacing.lg,
                top = SegueSpacing.sm,
                bottom = bottom + SegueSpacing.lg,
            ),
            modifier = Modifier.weight(1f),
        )
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
            onShuffleAlbumsClick = {},
        )
    }
}
