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
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaActions
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A [ContextMenuSheet] whose rows are [actions], labelled and iconed consistently across screens.
 * [onAction] runs before [onDismissRequest].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaContextMenu(
    header: ContextMenuHeader,
    actions: ImmutableList<MediaAction>,
    onAction: (MediaAction) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = mediaMenuItems(actions)
    ContextMenuSheet(
        header = header,
        items = items,
        onItemClick = { item ->
            onAction(MediaAction.valueOf(item.id))
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    )
}

@Composable
private fun mediaMenuItems(actions: ImmutableList<MediaAction>): ImmutableList<ContextMenuItem> {
    val labels = actions.map { stringResource(it.label) }
    return remember(actions, labels) {
        actions.mapIndexed { index, action ->
            ContextMenuItem(
                id = action.name,
                label = labels[index],
                icon = action.icon,
                destructive = action == MediaAction.Delete ||
                    action == MediaAction.RemoveFromPlaylist ||
                    action == MediaAction.RemoveDownload,
            )
        }.toImmutableList()
    }
}

private val MediaAction.label: Int
    get() = when (this) {
        MediaAction.Play -> R.string.ds_play
        MediaAction.Shuffle -> R.string.ds_shuffle
        MediaAction.ShuffleAlbums -> R.string.ds_action_shuffle_albums
        MediaAction.ShuffleArtistAlbums -> R.string.ds_action_shuffle_artist_albums
        MediaAction.PlayNext -> R.string.ds_action_play_next
        MediaAction.AddToQueue -> R.string.ds_action_add_to_queue
        MediaAction.InstantMix -> R.string.ds_action_instant_mix
        MediaAction.AddToPlaylist -> R.string.ds_action_add_to_playlist
        MediaAction.Favorite -> R.string.ds_action_favorite
        MediaAction.Unfavorite -> R.string.ds_action_unfavorite
        MediaAction.GoToAlbum -> R.string.ds_action_go_to_album
        MediaAction.GoToArtist -> R.string.ds_action_go_to_artist
        MediaAction.Download -> R.string.ds_action_download
        MediaAction.RemoveDownload -> R.string.ds_action_remove_download
        MediaAction.Rename -> R.string.ds_action_rename
        MediaAction.RemoveFromPlaylist -> R.string.ds_action_remove_from_playlist
        MediaAction.Delete -> R.string.ds_action_delete
    }

private val MediaAction.icon: ImageVector
    get() = when (this) {
        MediaAction.Play -> SegueIcons.Play
        MediaAction.Shuffle -> SegueIcons.Shuffle
        MediaAction.ShuffleAlbums, MediaAction.ShuffleArtistAlbums -> SegueIcons.ShuffleAlbums
        MediaAction.PlayNext -> SegueIcons.PlayNext
        MediaAction.AddToQueue -> SegueIcons.AddToQueue
        MediaAction.InstantMix -> SegueIcons.Related
        MediaAction.AddToPlaylist -> SegueIcons.AddToPlaylist
        MediaAction.Favorite -> SegueIcons.Heart
        MediaAction.Unfavorite -> SegueIcons.HeartFilled
        MediaAction.GoToAlbum -> SegueIcons.Disc3
        MediaAction.GoToArtist -> SegueIcons.Artist
        MediaAction.Download -> SegueIcons.Download
        MediaAction.Rename -> SegueIcons.Edit
        MediaAction.RemoveFromPlaylist, MediaAction.Delete, MediaAction.RemoveDownload -> SegueIcons.Remove
    }

@PreviewSegueThemes
@Composable
private fun MediaMenuPreview() {
    SeguePreview {
        ContextMenuContent(
            header = ContextMenuHeader(title = "Nude", subtitle = "Radiohead · In Rainbows", coverArtId = null),
            items = mediaMenuItems(MediaActions.song(isStarred = true, inPlaylist = true)),
            onItemClick = {},
        )
    }
}
