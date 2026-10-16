package com.quantumff.segue.feature.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.component.TrackRow
import com.quantumff.segue.core.designsystem.component.TrackRowState
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueMotion
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A queue track: the shared track row on a `primaryContainer` background when current, with a
 * `grip-vertical` drag handle on the right. [handleModifier] attaches the reorder gesture to the handle.
 * Other rows are transparent, so the queue also sits on Now Playing's art backdrop.
 */
@Composable
internal fun QueueTrackRow(
    state: TrackRowState,
    isDragging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val background = when {
        isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
        state.isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackRow(state = state, onClick = onClick, modifier = Modifier.weight(1f))
        DragHandle(stringResource(R.string.feature_queue_reorder), handleModifier)
    }
}

/**
 * Albums shuffle: the divider that starts an album block (DESIGN.md §4.8). It reads as a section
 * label, not a track: a hairline rule on top, then "Album · Artist · N tracks" as an overline,
 * with no art and no duration. Its `grip-horizontal` handle (tracks use `grip-vertical`) drags
 * the whole block.
 */
@Composable
internal fun AlbumBlockHeader(
    header: QueueListItem.AlbumHeader,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tracks = pluralStringResource(R.plurals.feature_queue_subtitle, header.trackCount, header.trackCount)
    val label = remember(header, tracks, colors.onSurface) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.onSurface)) { append(header.title) }
            header.artist?.let { append(SEPARATOR + it) }
            append(SEPARATOR + tracks)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AlbumHeaderHeight)
            .background(if (isDragging) colors.surfaceContainerHighest else Color.Transparent)
            .drawBehind { drawRect(colors.outlineVariant, size = Size(size.width, SegueSizes.hairline.toPx())) }
            .padding(start = SegueSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DragHandle(stringResource(R.string.feature_queue_move_album), handleModifier, SegueIcons.BlockDragHandle)
    }
}

/** "N played": the collapsed history at the top of the queue. Tap to expand or collapse. */
@Composable
internal fun HistoryRow(history: QueueListItem.History, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SegueSizes.touchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = SegueSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SegueIcons.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SegueSizes.iconSmall),
        )
        Spacer(Modifier.width(SegueSpacing.md))
        Text(
            text = pluralStringResource(R.plurals.feature_queue_played, history.count, history.count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = SegueIcons.ChevronDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(SegueSizes.iconSmall)
                .graphicsLayer { rotationZ = if (history.expanded) HALF_TURN else 0f },
        )
    }
}

@Composable
private fun DragHandle(label: String, modifier: Modifier = Modifier, icon: ImageVector = SegueIcons.DragHandle) {
    Box(
        modifier = modifier.size(SegueSizes.touchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SegueSizes.iconSmall),
        )
    }
}

/**
 * Symfonium's queue swipes (DESIGN.md §4.8): right = play next (`list-start`, `primary`), left =
 * remove (`trash`, `error`). Both trigger at 40% of the width, with a haptic tick at the threshold.
 * Offsets are read in the draw phase only, so dragging does not recompose.
 */
@Composable
internal fun QueueSwipeBox(
    onPlayNext: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var widthPx by remember { mutableFloatStateOf(0f) }
    var pastThreshold by remember { mutableStateOf(false) }
    val latestOnPlayNext by rememberUpdatedState(onPlayNext)
    val latestOnRemove by rememberUpdatedState(onRemove)
    val playNextLabel = stringResource(R.string.feature_queue_play_next)
    val removeLabel = stringResource(R.string.feature_queue_remove)

    val dragState = rememberDraggableState { delta ->
        val target = offset.value + delta
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(target) }
        val past = widthPx > 0f && abs(target) / widthPx >= SWIPE_THRESHOLD
        if (past && !pastThreshold) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        pastThreshold = past
    }
    Box(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .clipToBounds()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(playNextLabel) { latestOnPlayNext().let { true } },
                    CustomAccessibilityAction(removeLabel) { latestOnRemove().let { true } },
                )
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = {
                    pastThreshold = false
                    val fraction = if (widthPx > 0f) offset.value / widthPx else 0f
                    scope.launch {
                        when {
                            fraction >= SWIPE_THRESHOLD -> {
                                latestOnPlayNext()
                                offset.animateTo(0f, SegueMotion.player())
                            }

                            fraction <= -SWIPE_THRESHOLD -> {
                                offset.animateTo(-widthPx, SegueMotion.medium())
                                latestOnRemove()
                                // The row normally leaves the list now; come back if it did not.
                                delay(REMOVE_CONFIRM_TIMEOUT_MILLIS)
                                offset.animateTo(0f, SegueMotion.player())
                            }

                            else -> offset.animateTo(0f, SegueMotion.player())
                        }
                    }
                },
            ),
    ) {
        SwipeBackground(offset = { offset.value }, modifier = Modifier.matchParentSize())
        // Rows are transparent at rest; while swiped the row is opaque over the revealed actions.
        val rowFill = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .drawBehind { if (offset.value != 0f) drawRect(rowFill) },
        ) { content() }
    }
}

@Composable
private fun SwipeBackground(offset: () -> Float, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = modifier
            // Drawn only while swiping: at rest the transparent rows would show it.
            .drawBehind { if (offset() != 0f) drawRect(fill) }
            .padding(horizontal = SegueSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SegueIcons.PlayNext,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer { alpha = if (offset() > 0f) 1f else 0f },
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = SegueIcons.Remove,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.graphicsLayer { alpha = if (offset() < 0f) 1f else 0f },
        )
    }
}

private val AlbumHeaderHeight = 40.dp
private const val SEPARATOR = " · "
private const val SWIPE_THRESHOLD = 0.4f
private const val HALF_TURN = 180f
private const val REMOVE_CONFIRM_TIMEOUT_MILLIS = 1_500L
