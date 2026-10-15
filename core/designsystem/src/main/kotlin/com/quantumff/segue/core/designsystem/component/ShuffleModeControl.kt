package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.navigationIndicator
import com.quantumff.segue.core.model.ShuffleMode

/**
 * Queue shuffle control (DESIGN.md §4.8): a full-width, 40-tall segmented row
 * `Off | Tracks | Albums`, with the shuffle-albums icon on Albums.
 */
@Composable
fun ShuffleModeControl(selected: ShuffleMode, onSelect: (ShuffleMode) -> Unit, modifier: Modifier = Modifier) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.navigationIndicator,
        activeContentColor = MaterialTheme.colorScheme.onSurface,
        activeBorderColor = MaterialTheme.colorScheme.outline,
        // Transparent, so it also sits on Now Playing's art backdrop (the Up next panel).
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        inactiveBorderColor = MaterialTheme.colorScheme.outline,
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .height(SegueSizes.shuffleControl),
    ) {
        ShuffleMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ShuffleMode.entries.size),
                colors = colors,
                icon = {
                    if (mode == ShuffleMode.Albums) {
                        Icon(
                            imageVector = SegueIcons.ShuffleAlbums,
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    } else {
                        SegmentedButtonDefaults.Icon(active = mode == selected)
                    }
                },
            ) {
                Text(text = stringResource(mode.label), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private val ShuffleMode.label: Int
    get() = when (this) {
        ShuffleMode.Off -> R.string.ds_shuffle_off
        ShuffleMode.Tracks -> R.string.ds_shuffle_tracks
        ShuffleMode.Albums -> R.string.ds_shuffle_albums
    }

@PreviewSegueThemes
@Composable
private fun ShuffleModeControlPreview() {
    SeguePreview {
        ShuffleModeControl(
            selected = ShuffleMode.Albums,
            onSelect = {},
            modifier = Modifier.padding(SegueSpacing.lg),
        )
    }
}
