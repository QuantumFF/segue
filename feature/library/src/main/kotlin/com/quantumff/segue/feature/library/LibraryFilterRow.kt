package com.quantumff.segue.feature.library

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import com.quantumff.segue.core.designsystem.component.PickerSheet
import com.quantumff.segue.core.designsystem.component.SegueFilterChip
import com.quantumff.segue.core.designsystem.component.SegueSortChip
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.ArtistSortOrder
import com.quantumff.segue.core.model.LibraryTab
import com.quantumff.segue.core.model.SongSortOrder
import java.text.NumberFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal enum class LibraryPicker {
    Sort,
    Year,
    Genre,
}

/**
 * The 48-tall filter row (DESIGN.md §4.2): chips on the left, the item count on the right in its own slot.
 * The chips scroll inside 16dp margins when they don't fit, fading out at their trailing edge before
 * the count; when they all fit, nothing scrolls or fades.
 */
@Composable
internal fun FilterRow(state: LibraryUiState, onEvent: (LibraryEvent) -> Unit, onPickerClick: (LibraryPicker) -> Unit) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SegueSizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fadeTrailingEdge(scroll)
                .horizontalScroll(scroll)
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

                LibraryTab.Songs -> {
                    // First, so the tab's main action never scrolls out of sight on a narrow screen.
                    if (state.showsShuffleAll) {
                        ActionChip(
                            label = stringResource(R.string.feature_library_shuffle_all),
                            icon = SegueIcons.Shuffle,
                            onClick = { onEvent(LibraryEvent.ShuffleAll) },
                        )
                    }
                    SortChip(state, onClick = { onPickerClick(LibraryPicker.Sort) })
                    FavoritesDownloadedChips(state, LibraryTab.Songs, onEvent)
                }

                else -> Unit
            }
        }
        itemCountLabel(state)?.let { count ->
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                // No start gap: the chips fade out before they reach the count.
                modifier = Modifier.padding(end = SegueSpacing.lg),
            )
        }
    }
}

/**
 * Fades the content out toward its trailing edge while [scroll] can go further, so chips cut off by the
 * edge read as more to scroll to. The fade shortens as the end comes into view and is gone once there.
 */
private fun Modifier.fadeTrailingEdge(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = minOf(FILTER_ROW_FADE.toPx(), (scroll.maxValue - scroll.value).toFloat())
        if (fade <= 0f) return@drawWithContent
        val (start, end) = if (layoutDirection == LayoutDirection.Ltr) {
            size.width - fade to size.width
        } else {
            fade to 0f
        }
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = start, endX = end),
            blendMode = BlendMode.DstIn,
        )
    }

private val FILTER_ROW_FADE = SegueSpacing.xl

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
    SegueSortChip(label = stringResource(sort.currentLabel), onClick = onClick)
}

/**
 * The Favorites and Downloaded chips of [tab], shared by every tab that has them (DESIGN.md §4.2).
 * Each cycles through on, inverted and off; offline locks Downloaded on, since only downloaded
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
        lockedOffline = chips.isOffline,
        onClick = { onEvent(LibraryEvent.CycleDownloaded(tab)) },
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
internal fun LibraryPickerSheet(
    picker: LibraryPicker,
    state: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val any = stringResource(R.string.feature_library_filter_any)
    when (picker) {
        LibraryPicker.Sort -> tabSort(state)?.SortPickerSheet(onEvent, onDismissRequest)

        // A null option is "Any": no filter.
        LibraryPicker.Year -> PickerSheet(
            title = stringResource(R.string.feature_library_filter_year),
            options = (listOf(null) + state.years).toImmutableList(),
            selected = state.albumFilter.year,
            label = { it?.toString() ?: any },
            onSelect = { onEvent(LibraryEvent.SetYearFilter(it)) },
            onDismissRequest = onDismissRequest,
        )

        LibraryPicker.Genre -> PickerSheet(
            title = stringResource(R.string.feature_library_filter_genre),
            options = (listOf(null) + state.genres.map { it.name }).toImmutableList(),
            selected = state.albumFilter.genre,
            label = { it ?: any },
            onSelect = { onEvent(LibraryEvent.SetGenreFilter(it)) },
            onDismissRequest = onDismissRequest,
        )
    }
}

/**
 * A tab's sort chip: the [current] sort, the sorts its picker offers and the event that picks one.
 * A tab that gains a sort adds a branch to [tabSort].
 */
private class TabSort<S : Enum<S>>(
    private val current: S,
    private val options: ImmutableList<S>,
    private val label: (S) -> Int,
    private val event: (S) -> LibraryEvent,
) {
    val currentLabel: Int get() = label(current)

    @Composable
    fun SortPickerSheet(onEvent: (LibraryEvent) -> Unit, onDismissRequest: () -> Unit) {
        PickerSheet(
            title = stringResource(R.string.feature_library_sort),
            options = options,
            selected = current,
            label = { stringResource(label(it)) },
            onSelect = { onEvent(event(it)) },
            onDismissRequest = onDismissRequest,
        )
    }
}

/** The sort of the tab on show; null for a tab without one. */
private fun tabSort(state: LibraryUiState): TabSort<*>? = when (state.tab) {
    LibraryTab.Albums ->
        TabSort(state.albumSort, AlbumSortOrder.entries.toImmutableList(), { it.label }, LibraryEvent::SetAlbumSort)

    LibraryTab.Artists ->
        TabSort(state.artistSort, ArtistSortOrder.entries.toImmutableList(), { it.label }, LibraryEvent::SetArtistSort)

    LibraryTab.Songs ->
        TabSort(state.songSort, SongSortOrder.entries.toImmutableList(), { it.label }, LibraryEvent::SetSongSort)

    LibraryTab.Playlists, LibraryTab.Genres, LibraryTab.Downloads -> null
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

internal val SongSortOrder.label: Int
    get() = when (this) {
        SongSortOrder.Title -> R.string.feature_library_sort_title
        SongSortOrder.Artist -> R.string.feature_library_sort_artist
        SongSortOrder.Album -> R.string.feature_library_sort_album
        SongSortOrder.RecentlyAdded -> R.string.feature_library_sort_recently_added
        SongSortOrder.Year -> R.string.feature_library_sort_year
        SongSortOrder.PlayCount -> R.string.feature_library_sort_play_count
    }
