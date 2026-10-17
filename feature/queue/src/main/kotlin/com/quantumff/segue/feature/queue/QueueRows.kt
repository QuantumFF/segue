package com.quantumff.segue.feature.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quantumff.segue.core.designsystem.component.SwipeAction
import com.quantumff.segue.core.designsystem.component.SwipeActionBox
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
private fun DragHandle(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(SegueSizes.touchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SegueIcons.DragHandle,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SegueSizes.iconSmall),
        )
    }
}

/**
 * Symfonium's queue swipes (DESIGN.md §4.8): right = play next (`list-start`, `primary`), left =
 * remove (`trash`, `error`), which slides the row out. Both trigger at 40% of the width, with a
 * haptic tick at the threshold.
 */
@Composable
internal fun QueueSwipeBox(
    onPlayNext: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val playNextLabel = stringResource(R.string.feature_queue_play_next)
    val removeLabel = stringResource(R.string.feature_queue_remove)
    SwipeActionBox(
        start = remember(primary, playNextLabel) { SwipeAction(SegueIcons.PlayNext, primary, playNextLabel) },
        onStart = onPlayNext,
        end = remember(error, removeLabel) { SwipeAction(SegueIcons.Remove, error, removeLabel, dismisses = true) },
        onEnd = onRemove,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

private const val HALF_TURN = 180f
