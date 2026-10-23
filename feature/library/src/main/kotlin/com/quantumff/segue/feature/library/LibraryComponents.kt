package com.quantumff.segue.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.quantumff.segue.core.designsystem.component.AlbumCard
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.model.toBadgeState
import com.quantumff.segue.core.designsystem.model.toCardState
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.Playlist
import java.text.NumberFormat
import kotlin.math.abs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * Paged album grid (DESIGN.md §4.2): `GridCells.Adaptive(150)` unless [columns] says otherwise,
 * gutter 12, side margin 16. Placeholders keep the scroll position stable while pages load.
 * [selected] albums carry multi-select's mark. [artDisplaySize] should match the cell width. A
 * [zoom] glides the cards to their new places when a pinch changes the columns.
 */
@Composable
internal fun PagedAlbumGrid(
    albums: LazyPagingItems<Album>,
    onAlbumClick: (Album) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onAlbumLongClick: ((index: Int, album: Album) -> Unit)? = null,
    downloads: ImmutableMap<String, DownloadStatus> = persistentMapOf(),
    state: LazyGridState = rememberLazyGridState(),
    selected: ImmutableSet<String> = persistentSetOf(),
    onAlbumLongClickLabel: String? = null,
    columns: GridCells = GridCells.Adaptive(SegueSizes.albumGridCell),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(SegueSpacing.gutter),
    artDisplaySize: Dp = SegueSizes.albumGridCell,
    zoom: GridZoom? = null,
) {
    LazyVerticalGrid(
        columns = columns,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg),
        modifier = modifier,
    ) {
        items(
            count = albums.itemCount,
            key = albums.itemKey { it.id },
            contentType = albums.itemContentType { "album" },
        ) { index ->
            val album = albums[index]
            val placement = if (zoom == null) Modifier else Modifier.reflow(zoom, index)
            if (album == null) {
                AlbumPlaceholder(placement)
            } else {
                val download = downloads[album.id]
                AlbumCard(
                    state = remember(album, download) { album.toCardState(download = download?.toBadgeState()) },
                    onClick = { onAlbumClick(album) },
                    onLongClick = onAlbumLongClick?.let { { it(index, album) } },
                    artDisplaySize = artDisplaySize,
                    selected = album.id in selected,
                    onLongClickLabel = onAlbumLongClickLabel,
                    modifier = placement,
                )
            }
        }
    }
}

/** A not-yet-loaded grid cell: the card's art square on `surfaceContainerHigh`. */
@Composable
private fun AlbumPlaceholder(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(SegueShapes.md)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/** A filter-row chip that opens a picker: label plus a chevron, styled like [com.quantumff.segue.core.designsystem.component.SegueFilterChip]. */
@Composable
internal fun DropdownChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        trailingIcon = {
            Icon(
                imageVector = SegueIcons.ChevronDown,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        shape = SegueShapes.full,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
            borderWidth = SegueSizes.hairline,
            selectedBorderWidth = SegueSizes.hairline,
        ),
        modifier = modifier.height(SegueSizes.chip),
    )
}

/** One radio row in a [PickerSheet]. */
@Immutable
internal data class PickerOption(val key: String, val label: String)

/** A picker as a bottom sheet of radio rows (DESIGN.md §4.10: pickers never use dialogs). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerSheet(
    title: String,
    options: ImmutableList<PickerOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SegueShapes.sheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = SegueSizes.noElevation,
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm),
        )
        LazyColumn(
            modifier = Modifier
                .selectableGroup()
                .navigationBarsPadding(),
        ) {
            items(options, key = { it.key }) { option ->
                val selected = option.key == selectedKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SegueSizes.sheetRow)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                onSelect(option.key)
                                onDismissRequest()
                            },
                        )
                        .padding(horizontal = SegueSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.size(SegueSpacing.lg))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Genre tile (DESIGN.md §4.2): 72 tall, radius `md`, background hashed from the genre name and
 * clamped like a hero tint, name in `titleSmall`, counts in `bodySmall`, plus a "Shuffle albums" button.
 */
@Composable
internal fun GenreTile(
    genre: Genre,
    onClick: () -> Unit,
    onShuffleAlbumsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = NumberFormat.getIntegerInstance().format(genre.albumCount)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GENRE_TILE_HEIGHT)
            .clip(SegueShapes.md)
            .background(genreTint(genre.name, SegueTheme.isDark))
            .clickable(onClick = onClick)
            .padding(start = SegueSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = genre.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.feature_library_album_count, genre.albumCount, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        SegueIconButton(
            icon = SegueIcons.ShuffleAlbums,
            contentDescription = stringResource(R.string.feature_library_shuffle_genre_albums, genre.name),
            onClick = onShuffleAlbumsClick,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal val GENRE_TILE_HEIGHT = SegueSizes.mediaRow + SegueSpacing.sm

/**
 * A stable color per genre name, clamped like a hero tint (DESIGN.md §2.1): saturation at most
 * 0.55, lightness 0.18–0.30 on dark themes and 0.82–0.90 on light.
 */
internal fun genreTint(name: String, isDark: Boolean): Color {
    val hash = name.trim().lowercase().hashCode()
    val hue = abs(hash % HUE_RANGE).toFloat()
    val saturation = MIN_SATURATION + fraction(hash, SATURATION_SHIFT) * (MAX_SATURATION - MIN_SATURATION)
    val lightness = if (isDark) {
        DARK_LIGHTNESS.start + fraction(hash, LIGHTNESS_SHIFT) * (DARK_LIGHTNESS.endInclusive - DARK_LIGHTNESS.start)
    } else {
        LIGHT_LIGHTNESS.start + fraction(hash, LIGHTNESS_SHIFT) * (LIGHT_LIGHTNESS.endInclusive - LIGHT_LIGHTNESS.start)
    }
    return Color.hsl(hue, saturation, lightness)
}

private fun fraction(hash: Int, shift: Int): Float = (hash ushr shift and BYTE_MASK) / BYTE_MASK.toFloat()

private const val HUE_RANGE = 360
private const val MIN_SATURATION = 0.30f
private const val MAX_SATURATION = 0.55f
private val DARK_LIGHTNESS = 0.18f..0.30f
private val LIGHT_LIGHTNESS = 0.82f..0.90f
private const val SATURATION_SHIFT = 9
private const val LIGHTNESS_SHIFT = 17
private const val BYTE_MASK = 0xFF

/** "12 tracks · 48 min" or "40 tracks · 2 h 4 m". */
@Composable
internal fun playlistSubtitle(playlist: Playlist): String {
    val tracks = pluralStringResource(R.plurals.feature_library_track_count, playlist.songCount, playlist.songCount)
    val minutesTotal = playlist.durationSeconds / SECONDS_PER_MINUTE
    val duration = if (minutesTotal >= MINUTES_PER_HOUR) {
        stringResource(
            R.string.feature_library_duration_hours,
            minutesTotal / MINUTES_PER_HOUR,
            minutesTotal % MINUTES_PER_HOUR,
        )
    } else {
        stringResource(R.string.feature_library_duration_minutes, minutesTotal)
    }
    return stringResource(R.string.feature_library_separator, tracks, duration)
}

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

@PreviewSegueThemes
@Composable
private fun GenreTilePreview() {
    SeguePreview {
        Column(
            verticalArrangement = Arrangement.spacedBy(SegueSpacing.gutter),
            modifier = Modifier.padding(SegueSpacing.lg),
        ) {
            persistentListOf(Genre("Rock", 120, 12), Genre("Electronic", 80, 9), Genre("Jazz", 40, 1)).forEach {
                GenreTile(genre = it, onClick = {}, onShuffleAlbumsClick = {})
            }
        }
    }
}
