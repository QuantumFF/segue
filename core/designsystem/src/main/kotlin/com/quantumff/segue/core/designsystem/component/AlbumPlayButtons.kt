package com.quantumff.segue.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.LocalReduceMotion
import com.quantumff.segue.core.designsystem.theme.SegueBreakpoints
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.QueuePlacement

/**
 * "Play albums" and "Shuffle albums" for a list of albums (DESIGN.md §5.9): two split pills, 48
 * tall, labelled "Play" and "Shuffle" (the full names are their content descriptions). Tapping a
 * pill plays now (replaces the queue); its chevron, or a long press on the pill,
 * opens "Play next" / "Add to queue". With [expanded] false only the icons show.
 */
@Composable
fun AlbumPlayButtons(
    onPlay: (order: AlbumOrder, placement: QueuePlacement) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm)) {
        SplitPill(
            icon = SegueIcons.PlayFilled,
            label = stringResource(R.string.ds_play),
            description = stringResource(R.string.ds_play_albums),
            expanded = expanded,
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            border = null,
            onPlace = { onPlay(AlbumOrder.InOrder, it) },
        )
        SplitPill(
            icon = SegueIcons.ShuffleAlbums,
            label = stringResource(R.string.ds_shuffle),
            description = stringResource(R.string.ds_action_shuffle_albums),
            expanded = expanded,
            containerColor = colors.surfaceContainerHighest,
            contentColor = colors.onSurface,
            border = BorderStroke(SegueSizes.hairline, colors.outlineVariant),
            onPlace = { onPlay(AlbumOrder.Shuffled, it) },
        )
    }
}

/**
 * [AlbumPlayButtons] floating at the bottom start of this box, just above the mini player and
 * navigation bar ([LocalShellContentPadding]), at the screen side margin (16 on compact width, 24
 * wider). Hidden while the keyboard is up. Lists under it add [FloatingAlbumPlayButtonsInset] to
 * their bottom padding so their last items can scroll clear of it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.FloatingAlbumPlayButtons(
    visible: Boolean,
    onPlay: (order: AlbumOrder, placement: QueuePlacement) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    BoxWithConstraints(modifier.matchParentSize()) {
        val margin = if (maxWidth >= SegueBreakpoints.medium) {
            SegueSpacing.screenMarginWide
        } else {
            SegueSpacing.screenMarginCompact
        }
        AnimatedVisibility(
            visible = visible && !WindowInsets.isImeVisible,
            enter = fadeIn(SegueMotion.medium()),
            exit = fadeOut(SegueMotion.medium()),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = margin, end = margin, bottom = bottom + SegueSpacing.lg),
        ) {
            AlbumPlayButtons(onPlay = onPlay, expanded = expanded)
        }
    }
}

/** Extra bottom padding for a list under [FloatingAlbumPlayButtons]: the pills, their margin, and a gap. */
val FloatingAlbumPlayButtonsInset: Dp = SegueSizes.touchTarget + SegueSpacing.lg + SegueSpacing.sm

/**
 * Collapses floating buttons while the user scrolls a list down and expands them on the way back
 * up. Attach [connection] with `Modifier.nestedScroll` around the list.
 */
@Stable
class CollapseOnScrollState {
    var expanded by mutableStateOf(true)
        private set

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y < 0f) {
                expanded = false
            } else if (consumed.y > 0f) {
                expanded = true
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberCollapseOnScrollState(): CollapseOnScrollState = remember { CollapseOnScrollState() }

/** A pill that plays on tap, with an attached chevron (and long press) for queue placements. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SplitPill(
    icon: ImageVector,
    label: String,
    // The full action ("Shuffle albums") for screen readers; the pill shows the short [label].
    description: String,
    expanded: Boolean,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke?,
    onPlace: (QueuePlacement) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val menuLabel = stringResource(R.string.ds_queue_options, description)
    Box {
        Row(
            modifier = Modifier
                .height(SegueSizes.touchTarget)
                .clip(SegueShapes.full)
                .background(containerColor)
                .then(if (border != null) Modifier.border(border, SegueShapes.full) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { onPlace(QueuePlacement.Replace) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        },
                        onLongClickLabel = menuLabel,
                    )
                    .semantics { contentDescription = description }
                    .padding(start = SegueSpacing.lg, end = SegueSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(SegueSizes.iconSmall),
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = if (reduceMotion) {
                        fadeIn(SegueMotion.medium())
                    } else {
                        expandHorizontally(SegueMotion.medium()) + fadeIn(SegueMotion.medium())
                    },
                    exit = if (reduceMotion) {
                        fadeOut(SegueMotion.medium())
                    } else {
                        shrinkHorizontally(SegueMotion.medium()) + fadeOut(SegueMotion.medium())
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(SegueSpacing.sm))
                        Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1)
                    }
                }
            }
            Box(
                Modifier
                    .width(SegueSizes.hairline)
                    .height(SegueSizes.iconSmall)
                    .background(contentColor.copy(alpha = DIVIDER_ALPHA)),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(role = Role.Button, onClickLabel = menuLabel) { menuOpen = true }
                    .semantics { contentDescription = menuLabel }
                    .padding(start = SegueSpacing.sm, end = SegueSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SegueIcons.ChevronDown,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(SegueSizes.iconSmall),
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = SegueShapes.lg,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            PlacementItem(SegueIcons.PlayNext, stringResource(R.string.ds_action_play_next)) {
                menuOpen = false
                onPlace(QueuePlacement.Next)
            }
            PlacementItem(SegueIcons.AddToQueue, stringResource(R.string.ds_action_add_to_queue)) {
                menuOpen = false
                onPlace(QueuePlacement.End)
            }
        }
    }
}

/** A 48-tall menu row with a 20 icon and a `bodyLarge` label (DESIGN.md §5.7). */
@Composable
private fun PlacementItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(SegueSizes.iconSmall)) },
        onClick = onClick,
        modifier = Modifier.height(SegueSizes.sheetRow),
    )
}

private const val DIVIDER_ALPHA = 0.24f

@PreviewSegueThemes
@Composable
private fun AlbumPlayButtonsPreview() {
    SeguePreview {
        Column(verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg)) {
            AlbumPlayButtons(onPlay = { _, _ -> })
            AlbumPlayButtons(onPlay = { _, _ -> }, expanded = false)
        }
    }
}
