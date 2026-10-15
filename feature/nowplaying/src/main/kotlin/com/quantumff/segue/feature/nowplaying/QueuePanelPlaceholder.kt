package com.quantumff.segue.feature.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.icon.SegueIcons

/**
 * Stand-in for the Up next panel until the queue feature provides its list composable: points to
 * the queue screen. The app passes it as Now Playing's `queueContent`.
 */
@Composable
fun QueuePanelPlaceholder(onOpenQueue: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = SegueIcons.UpNext,
        title = stringResource(R.string.feature_nowplaying_queue_placeholder_title),
        message = stringResource(R.string.feature_nowplaying_queue_placeholder_message),
        actionLabel = stringResource(R.string.feature_nowplaying_queue_placeholder_action),
        onActionClick = onOpenQueue,
        modifier = modifier,
    )
}
