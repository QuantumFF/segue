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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing

/**
 * Filter chip (DESIGN.md §5.6): 32 tall, radius `full`, `labelMedium`. Unselected is transparent
 * with an `outlineVariant` border; selected is `surfaceContainerHighest` with a leading check.
 */
@Composable
fun SegueFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = SegueIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        shape = SegueShapes.full,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
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
            .background(MaterialTheme.colorScheme.background.copy(alpha = PILL_FILL_ALPHA))
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

private const val PILL_FILL_ALPHA = 0.5f

@PreviewSegueThemes
@Composable
private fun ChipsPreview() {
    SeguePreview {
        Column(Modifier.padding(SegueSpacing.lg), verticalArrangement = Arrangement.spacedBy(SegueSpacing.sm)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm)) {
                SegueFilterChip(label = "Favorites", selected = true, onClick = {})
                SegueFilterChip(label = "Downloaded", selected = false, onClick = {})
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm)) {
                Pill(label = "Alternative", onClick = {})
                Pill(label = "Rock", onClick = {})
            }
            StatusChip(label = stringResource(R.string.ds_offline_cached), onClick = {})
        }
    }
}
