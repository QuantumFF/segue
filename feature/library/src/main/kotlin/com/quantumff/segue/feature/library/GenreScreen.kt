package com.quantumff.segue.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtons
import com.quantumff.segue.core.designsystem.component.FloatingAlbumPlayButtonsInset
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.component.rememberCollapseOnScrollState
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.QueuePlacement
import java.text.NumberFormat
import kotlinx.coroutines.flow.flowOf

/** One genre's albums: a secondary top bar, the album grid, and the floating Play / Shuffle albums buttons. */
@Composable
fun GenreScreen(
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GenreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GenreContent(
        state = state,
        albums = viewModel.albums.collectAsLazyPagingItems(),
        onBackClick = onBackClick,
        onAlbumClick = onAlbumClick,
        onPlayAlbums = viewModel::playAlbums,
        modifier = modifier,
    )
}

@Composable
internal fun GenreContent(
    state: GenreUiState,
    albums: LazyPagingItems<Album>,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlayAlbums: (AlbumOrder, QueuePlacement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = state.albumCount?.let {
        pluralStringResource(R.plurals.feature_library_album_count, it, NumberFormat.getIntegerInstance().format(it))
    }
    val collapse = rememberCollapseOnScrollState()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SubScreenTopBar(title = state.genre, subtitle = count, onBackClick = onBackClick)
            val bottom = LocalShellContentPadding.current.calculateBottomPadding()
            PagedAlbumGrid(
                albums = albums,
                onAlbumClick = { onAlbumClick(it.id) },
                contentPadding = PaddingValues(
                    start = SegueSpacing.lg,
                    end = SegueSpacing.lg,
                    top = SegueSpacing.sm,
                    bottom = bottom + FloatingAlbumPlayButtonsInset,
                ),
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(collapse.connection),
            )
        }
        FloatingAlbumPlayButtons(
            visible = (state.albumCount ?: 0) > 0,
            expanded = collapse.expanded,
            onPlay = onPlayAlbums,
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
            onPlayAlbums = { _, _ -> },
        )
    }
}
