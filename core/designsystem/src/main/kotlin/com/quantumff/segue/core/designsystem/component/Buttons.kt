package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes

/**
 * Circular `primary` play button. 56 on hero action rows, 36 in the collapsed detail top bar,
 * 48 on feature carousel items. Shows pause while [isPlaying].
 */
@Composable
fun PlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    size: Dp = SegueSizes.primaryPlayButton,
) {
    FilledIconButton(
        onClick = onClick,
        shape = SegueShapes.full,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier.size(size),
    ) {
        Icon(
            imageVector = if (isPlaying) SegueIcons.PauseFilled else SegueIcons.PlayFilled,
            contentDescription = stringResource(if (isPlaying) R.string.ds_pause else R.string.ds_play),
            modifier = Modifier.size(if (size < SegueSizes.touchTarget) SegueSizes.iconSmall else SegueSizes.icon),
        )
    }
}

/** A 48 icon button with a 24 icon, for hero action rows and top bars. */
@Composable
fun SegueIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(SegueSizes.touchTarget)) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}

@PreviewSegueThemes
@Composable
private fun ButtonsPreview() {
    SeguePreview {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayButton(onClick = {})
            PlayButton(onClick = {}, isPlaying = true, size = SegueSizes.detailTopBarPlay)
            SegueIconButton(icon = SegueIcons.Shuffle, contentDescription = null, onClick = {})
            SegueIconButton(icon = SegueIcons.HeartFilled, contentDescription = null, onClick = {})
        }
    }
}
