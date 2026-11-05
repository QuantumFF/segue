package com.quantumff.segue.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.PinAction
import com.quantumff.segue.core.model.pinActions
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The download sheet an album, artist or playlist page opens from its download button once the page
 * is pinned: the [actions][pinActions] that fit the pin's [status], so a failed download offers
 * Retry, a paused one Resume, and one still downloading Pause and Cancel. [onAction] runs before
 * [onDismissRequest].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(
    header: ContextMenuHeader,
    status: DownloadStatus,
    onAction: (PinAction) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContextMenuSheet(
        header = header,
        items = pinMenuItems(status.pinActions),
        onItemClick = { item ->
            onAction(PinAction.valueOf(item.id))
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    )
}

@Composable
private fun pinMenuItems(actions: ImmutableList<PinAction>): ImmutableList<ContextMenuItem> {
    val labels = actions.map { stringResource(it.label) }
    return remember(actions, labels) {
        actions.mapIndexed { index, action ->
            ContextMenuItem(
                id = action.name,
                label = labels[index],
                icon = action.icon,
                destructive = action == PinAction.Cancel || action == PinAction.Remove,
            )
        }.toImmutableList()
    }
}

private val PinAction.label: Int
    get() = when (this) {
        PinAction.Retry -> R.string.ds_pin_retry
        PinAction.Resume -> R.string.ds_pin_resume
        PinAction.Pause -> R.string.ds_pin_pause
        PinAction.Cancel -> R.string.ds_pin_cancel
        PinAction.Remove -> R.string.ds_action_remove_download
    }

private val PinAction.icon: ImageVector
    get() = when (this) {
        PinAction.Retry -> SegueIcons.Refresh
        PinAction.Resume -> SegueIcons.Play
        PinAction.Pause -> SegueIcons.Pause
        PinAction.Cancel -> SegueIcons.Close
        PinAction.Remove -> SegueIcons.Remove
    }

@PreviewSegueThemes
@Composable
private fun DownloadSheetPreview() {
    SeguePreview {
        ContextMenuContent(
            header = ContextMenuHeader(title = "In Rainbows", subtitle = "Radiohead", coverArtId = null),
            items = pinMenuItems(DownloadStatus.Failed.pinActions),
            onItemClick = {},
        )
    }
}
