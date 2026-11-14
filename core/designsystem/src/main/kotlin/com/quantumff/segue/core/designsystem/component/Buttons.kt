package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes

/**
 * Circular `primary` play button. 56 on hero action rows, 36 in the collapsed detail top bar,
 * 48 on feature carousel items. Shows pause while [isPlaying]; disabled when nothing could play.
 * While [loading] (its play is being prepared) the icon turns into the inline spinner (DESIGN.md
 * §5.8), keeping the button's name.
 */
@Composable
fun PlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    size: Dp = SegueSizes.primaryPlayButton,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = SegueShapes.full,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .size(size)
            .loadingSemantics(loading, stringResource(if (isPlaying) R.string.ds_pause else R.string.ds_play)),
    ) {
        if (loading) {
            InlineSpinner(color = LocalContentColor.current)
        } else {
            Icon(
                imageVector = if (isPlaying) SegueIcons.PauseFilled else SegueIcons.PlayFilled,
                contentDescription = stringResource(if (isPlaying) R.string.ds_pause else R.string.ds_play),
                modifier = Modifier.size(if (size < SegueSizes.touchTarget) SegueSizes.iconSmall else SegueSizes.icon),
            )
        }
    }
}

/**
 * A 48 icon button with a 24 icon, for hero action rows and top bars. While [loading] the icon
 * turns into the inline spinner (DESIGN.md §5.8) in the same slot, keeping the button's name.
 */
@Composable
fun SegueIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(SegueSizes.touchTarget)
            .loadingSemantics(loading, contentDescription),
    ) {
        if (loading) {
            InlineSpinner(color = LocalContentColor.current)
        } else {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

/** While [loading], the button keeps the name its icon gave it: the spinner in its place has none. */
private fun Modifier.loadingSemantics(loading: Boolean, contentDescription: String?): Modifier =
    if (loading && contentDescription != null) {
        semantics { this.contentDescription = contentDescription }
    } else {
        this
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
            PlayButton(onClick = {}, enabled = false, loading = true)
            SegueIconButton(icon = SegueIcons.Shuffle, contentDescription = null, onClick = {}, loading = true)
        }
    }
}
