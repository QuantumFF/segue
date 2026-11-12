package com.quantumff.segue.feature.queue

import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import com.quantumff.segue.core.designsystem.component.ContextMenuHeader
import com.quantumff.segue.core.designsystem.component.ContextMenuItem
import com.quantumff.segue.core.designsystem.component.ContextMenuSheet
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueBaseColors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import com.quantumff.segue.core.designsystem.R as DesignR

/** Rows of a queue track's ⋮ menu, in menu order (DESIGN.md §4.8). */
enum class QueueMenuAction {
    PlayNext,
    Remove,
    AddToPlaylist,
    GoToAlbum,
    GoToArtist,

    /** Favorite, or Unfavorite when the song is starred. */
    Favorite,
    Info,
}

/**
 * The rows of [track]'s menu, labelled by [label] from a string resource. Play next is left out
 * where it would not move the track, and Go to album or artist when the song has none. Remove
 * from queue is destructive, so it is drawn in `error`.
 */
internal fun queueMenuItems(track: QueueListItem.Track, label: (Int) -> String): ImmutableList<ContextMenuItem> =
    QueueMenuAction.entries
        .filter { action ->
            when (action) {
                QueueMenuAction.PlayNext -> track.canPlayNext
                QueueMenuAction.GoToAlbum -> track.song.albumId != null
                QueueMenuAction.GoToArtist -> track.song.artistId != null
                else -> true
            }
        }
        .map { action ->
            ContextMenuItem(
                id = action.name,
                label = label(action.label(track.song.isStarred)),
                icon = action.icon(track.song.isStarred),
                destructive = action == QueueMenuAction.Remove,
            )
        }
        .toImmutableList()

/**
 * A queue track's ⋮ menu: the shared context sheet. It is raised from Now Playing's Queue panel,
 * which draws on the art, so it uses the app's own colors (DESIGN.md §4.7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueTrackMenu(
    track: QueueListItem.Track,
    onAction: (QueueMenuAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val resources = LocalResources.current
    val items = remember(track, resources) { queueMenuItems(track, resources::getString) }
    SegueBaseColors {
        ContextMenuSheet(
            header = ContextMenuHeader(
                title = track.row.title,
                subtitle = listOfNotNull(track.row.artist.ifEmpty { null }, track.row.album).joinToString(" · ")
                    .ifEmpty { null },
                coverArtId = track.row.coverArtId,
            ),
            items = items,
            onItemClick = { item ->
                onDismissRequest()
                onAction(QueueMenuAction.valueOf(item.id))
            },
            onDismissRequest = onDismissRequest,
        )
    }
}

@StringRes
private fun QueueMenuAction.label(starred: Boolean): Int = when (this) {
    QueueMenuAction.PlayNext -> DesignR.string.ds_action_play_next
    QueueMenuAction.Remove -> R.string.feature_queue_menu_remove
    QueueMenuAction.AddToPlaylist -> DesignR.string.ds_action_add_to_playlist
    QueueMenuAction.GoToAlbum -> DesignR.string.ds_action_go_to_album
    QueueMenuAction.GoToArtist -> DesignR.string.ds_action_go_to_artist
    QueueMenuAction.Favorite -> if (starred) DesignR.string.ds_action_unfavorite else DesignR.string.ds_action_favorite
    QueueMenuAction.Info -> R.string.feature_queue_menu_info
}

private fun QueueMenuAction.icon(starred: Boolean): ImageVector = when (this) {
    QueueMenuAction.PlayNext -> SegueIcons.PlayNext
    QueueMenuAction.Remove -> SegueIcons.Remove
    QueueMenuAction.AddToPlaylist -> SegueIcons.AddToPlaylist
    QueueMenuAction.GoToAlbum -> SegueIcons.Disc3
    QueueMenuAction.GoToArtist -> SegueIcons.Artist
    QueueMenuAction.Favorite -> if (starred) SegueIcons.HeartFilled else SegueIcons.Heart
    QueueMenuAction.Info -> SegueIcons.Info
}
