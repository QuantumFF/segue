package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueAlpha
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.OfflineStatus

/**
 * Filter chip (DESIGN.md §5.6): 32 tall, radius `full`, `labelMedium`. Unselected is transparent
 * with an `outlineVariant` border; selected is `surfaceContainerHighest` with a leading check, or a
 * leading ✕ when [excluded] (it hides what it names).
 *
 * [lockedOffline] holds the chip on while Offline (the Downloaded chip, as only downloaded music
 * plays): it shows on with a muted leading lock, TalkBack hears "On while offline", and taps do
 * nothing. It stays enabled, so TalkBack doesn't add "disabled".
 */
@Composable
fun SegueFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    excluded: Boolean = false,
    lockedOffline: Boolean = false,
) {
    val shownSelected = selected || lockedOffline
    val state = when {
        lockedOffline -> stringResource(R.string.ds_filter_locked_offline)
        selected && excluded -> stringResource(R.string.ds_filter_excluded)
        else -> null
    }
    val leadingIcon = when {
        lockedOffline -> SegueIcons.Lock
        !selected -> null
        excluded -> SegueIcons.Close
        else -> SegueIcons.Check
    }
    FilterChip(
        selected = shownSelected,
        onClick = if (lockedOffline) ({}) else onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        },
        shape = SegueShapes.full,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            // Locked still reads as on; its muted lock says why it doesn't respond.
            selectedLeadingIconColor = if (lockedOffline) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = shownSelected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
            borderWidth = SegueSizes.hairline,
            selectedBorderWidth = SegueSizes.hairline,
        ),
        modifier = modifier
            .height(SegueSizes.chip)
            .then(if (state != null) Modifier.semantics { stateDescription = state } else Modifier),
    )
}

/**
 * Sort chip (DESIGN.md §4.2 filter row): looks like an unselected [SegueFilterChip] with a trailing
 * chevron, and opens the sort picker. It is a dropdown, not a filter, so TalkBack hears "Change
 * sort" and never a selected state.
 */
@Composable
fun SegueSortChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(SegueSizes.chip)
            .clip(SegueShapes.full)
            .border(SegueSizes.hairline, MaterialTheme.colorScheme.outlineVariant, SegueShapes.full)
            .clickable(
                role = Role.DropdownList,
                onClickLabel = stringResource(R.string.ds_change_sort),
                onClick = onClick,
            )
            .padding(start = SegueSpacing.lg, end = SegueSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(SegueSpacing.sm))
        Icon(
            imageVector = SegueIcons.ChevronDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FilterChipDefaults.IconSize),
        )
    }
}

/**
 * Feishin pill for genres and hero metadata (DESIGN.md §5.6): 28 tall, radius `full`,
 * `background` @ 50%, 1dp `outlineVariant` border, `labelMedium`, 12 horizontal padding.
 */
@Composable
fun Pill(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier = modifier
            .height(SegueSizes.pill)
            .clip(SegueShapes.full)
            .background(MaterialTheme.colorScheme.background.copy(alpha = SegueAlpha.PILL_FILL))
            .border(SegueSizes.hairline, MaterialTheme.colorScheme.outlineVariant, SegueShapes.full)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = SegueSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Small status chip under a top bar, e.g. "Offline · showing cached library" (DESIGN.md §5.8).
 * `surfaceContainerHighest`, a leading icon; tapping it retries.
 */
@Composable
fun StatusChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = SegueIcons.Offline,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .height(SegueSizes.chip)
            .clip(SegueShapes.full)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = SegueSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FilterChipDefaults.IconSize),
        )
        Spacer(Modifier.width(SegueSpacing.sm))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * The offline status chip under a screen's top bar or tab row (DESIGN.md §5.8), with copy for each
 * [status]: "Offline · showing downloads" or "Offline · showing cached library". Nothing shows
 * while [OfflineStatus.Online].
 */
@Composable
fun OfflineStatusChip(status: OfflineStatus, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val label = when (status) {
        OfflineStatus.Online -> return
        OfflineStatus.ShowingCachedLibrary -> R.string.ds_offline_cached
        OfflineStatus.ShowingDownloads -> R.string.ds_offline_downloads
    }
    StatusChip(label = stringResource(label), modifier = modifier, onClick = onClick)
}

@PreviewSegueThemes
@Composable
private fun ChipsPreview() {
    SeguePreview {
        Column(Modifier.padding(SegueSpacing.lg), verticalArrangement = Arrangement.spacedBy(SegueSpacing.sm)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm)) {
                SegueSortChip(label = "Recently added", onClick = {})
                SegueFilterChip(label = "Favorites", selected = true, onClick = {})
                SegueFilterChip(label = "Downloaded", selected = true, excluded = true, onClick = {})
                SegueFilterChip(label = "Downloaded", selected = false, lockedOffline = true, onClick = {})
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm)) {
                Pill(label = "Alternative", onClick = {})
                Pill(label = "Rock", onClick = {})
            }
            OfflineStatusChip(OfflineStatus.ShowingDownloads)
        }
    }
}
