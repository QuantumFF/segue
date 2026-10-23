package com.quantumff.segue.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.quantumff.segue.core.designsystem.component.AlbumCardSkeleton
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.ErrorState
import com.quantumff.segue.core.designsystem.component.SkeletonBlock
import com.quantumff.segue.core.designsystem.component.SkeletonPulse
import com.quantumff.segue.core.designsystem.component.TrackRowSkeleton
import com.quantumff.segue.core.designsystem.component.rememberDelayedVisibility
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.ScreenState

/** The placeholders a tab's skeleton draws, after the layout of its content. */
internal enum class TabSkeleton {
    /** An album or artist grid. */
    Cards,

    /** A list of rows. */
    Rows,

    /** The Genres tab's tiles. */
    Tiles,
}

/**
 * A Library tab with no content to show, in [screen]'s state (DESIGN.md §5.8): [skeleton]
 * placeholders while it loads, an error with Retry, or its [empty] state.
 */
@Composable
internal fun TabWithoutContent(
    screen: ScreenState,
    skeleton: TabSkeleton,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    empty: @Composable () -> Unit,
) {
    when (screen) {
        ScreenState.Loading -> TabSkeleton(skeleton, contentPadding)

        is ScreenState.Error -> ScrollableEmpty(contentPadding) {
            ErrorState(
                title = stringResource(R.string.feature_library_error_title),
                message = stringResource(R.string.feature_library_error_message),
                onRetryClick = onRetry,
            )
        }

        ScreenState.Empty -> empty()

        ScreenState.Content -> Unit
    }
}

/**
 * The Artists tab offline, where downloaded artists are not counted: what the paged artists say,
 * loading until their first page.
 */
internal fun LazyPagingItems<*>.screenState(): ScreenState = when {
    itemCount > 0 -> ScreenState.Content
    loadState.refresh is LoadState.Loading -> ScreenState.Loading
    else -> ScreenState.Empty
}

/** A tab with nothing synced yet, named after what it holds, with a sync button. */
@Composable
internal fun TabEmpty(tab: LibraryTab, contentPadding: PaddingValues, onSyncClick: () -> Unit) {
    val copy = when (tab) {
        LibraryTab.Artists -> EmptyCopy(
            SegueIcons.Artist,
            R.string.feature_library_empty_artists_title,
            R.string.feature_library_empty_artists_message,
        )

        LibraryTab.Songs -> EmptyCopy(
            SegueIcons.Music,
            R.string.feature_library_empty_songs_title,
            R.string.feature_library_empty_songs_message,
        )

        LibraryTab.Genres -> EmptyCopy(
            SegueIcons.Library,
            R.string.feature_library_empty_genres_title,
            R.string.feature_library_empty_genres_message,
        )

        else -> EmptyCopy(
            SegueIcons.Disc3,
            R.string.feature_library_empty_albums_title,
            R.string.feature_library_empty_albums_message,
        )
    }
    ScrollableEmpty(contentPadding) {
        EmptyState(
            icon = copy.icon,
            title = stringResource(copy.title),
            message = stringResource(copy.message),
            actionLabel = stringResource(R.string.feature_library_sync_now),
            onActionClick = onSyncClick,
        )
    }
}

private class EmptyCopy(val icon: ImageVector, @StringRes val title: Int, @StringRes val message: Int)

/** Skeletons, shown only once loading has taken longer than 150ms. */
@Composable
private fun TabSkeleton(kind: TabSkeleton, contentPadding: PaddingValues) {
    if (!rememberDelayedVisibility()) return
    SkeletonPulse {
        when (kind) {
            TabSkeleton.Cards -> SkeletonGrid(GridCells.Adaptive(SegueSizes.albumGridCell), contentPadding) {
                AlbumCardSkeleton(width = SegueSizes.albumGridCell)
            }

            TabSkeleton.Tiles -> SkeletonGrid(GridCells.Fixed(GENRE_COLUMNS), contentPadding) {
                SkeletonBlock(
                    Modifier
                        .fillMaxWidth()
                        .height(GENRE_TILE_HEIGHT),
                    shape = SegueShapes.md,
                )
            }

            TabSkeleton.Rows -> LazyColumn(
                contentPadding = contentPadding,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(SKELETON_ROWS, contentType = { "skeleton" }) { TrackRowSkeleton() }
            }
        }
    }
}

@Composable
private fun SkeletonGrid(columns: GridCells, contentPadding: PaddingValues, item: @Composable () -> Unit) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(SegueSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(SKELETON_CARDS, contentType = { "skeleton" }) { item() }
    }
}

private const val SKELETON_CARDS = 6
private const val SKELETON_ROWS = 10
