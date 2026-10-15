package com.quantumff.segue.feature.nowplaying.related

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.ContextMenuHeader
import com.quantumff.segue.core.designsystem.component.EmptyState
import com.quantumff.segue.core.designsystem.component.ErrorState
import com.quantumff.segue.core.designsystem.component.InlineSpinner
import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.designsystem.component.MediaContextMenu
import com.quantumff.segue.core.designsystem.component.TrackRow
import com.quantumff.segue.core.designsystem.component.TrackRowState
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.feature.nowplaying.R
import com.quantumff.segue.feature.nowplaying.RelatedUiState
import kotlinx.collections.immutable.persistentListOf

/** What the related panel can ask for. */
internal class RelatedActions(
    val onPlay: (index: Int) -> Unit,
    val onPlayNext: (Song) -> Unit,
    val onAddToQueue: (List<Song>) -> Unit,
    val onAddToPlaylist: (Song) -> Unit,
    val onAlbumClick: (String) -> Unit,
    val onArtistClick: (String) -> Unit,
    val onRetry: () -> Unit,
)

/**
 * Related panel (DESIGN.md §4.7): songs similar to the current artist (`getSimilarSongs2`) as track
 * rows, with "Play all" and "Add to queue" at the top. Tap plays from that song; ⋮ offers play next.
 */
@Composable
internal fun RelatedPanel(state: RelatedUiState, actions: RelatedActions, modifier: Modifier = Modifier) {
    when (state) {
        RelatedUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InlineSpinner() }

        RelatedUiState.Error -> ErrorState(
            title = stringResource(R.string.feature_nowplaying_related_error),
            message = null,
            onRetryClick = actions.onRetry,
            modifier = modifier,
        )

        RelatedUiState.Unavailable -> EmptyState(
            icon = SegueIcons.Related,
            title = stringResource(R.string.feature_nowplaying_related_empty_title),
            message = stringResource(R.string.feature_nowplaying_related_unavailable),
            modifier = modifier,
        )

        is RelatedUiState.Loaded -> if (state.songs.isEmpty()) {
            EmptyState(
                icon = SegueIcons.Related,
                title = stringResource(R.string.feature_nowplaying_related_empty_title),
                message = stringResource(R.string.feature_nowplaying_related_empty_message),
                modifier = modifier,
            )
        } else {
            RelatedSongs(state.songs, actions, modifier)
        }
    }
}

@Composable
private fun RelatedSongs(songs: List<Song>, actions: RelatedActions, modifier: Modifier = Modifier) {
    var menuFor by remember { mutableStateOf<Song?>(null) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = SegueSpacing.lg)) {
        item(key = "actions", contentType = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm),
            ) {
                FilledTonalButton(onClick = { actions.onPlay(0) }) {
                    Icon(SegueIcons.Play, contentDescription = null, modifier = Modifier.padding(end = SegueSpacing.sm))
                    Text(stringResource(R.string.feature_nowplaying_related_play_all))
                }
                OutlinedButton(onClick = { actions.onAddToQueue(songs) }) {
                    Icon(
                        SegueIcons.AddToQueue,
                        contentDescription = null,
                        modifier = Modifier.padding(end = SegueSpacing.sm),
                    )
                    Text(stringResource(R.string.feature_nowplaying_related_add_to_queue))
                }
            }
        }
        itemsIndexed(songs, key = { index, song ->
            "$index:${song.id}"
        }, contentType = { _, _ -> "song" }) { index, song ->
            TrackRow(
                state = song.toTrackRow(),
                onClick = { actions.onPlay(index) },
                onLongClick = { menuFor = song },
                onMoreClick = { menuFor = song },
            )
        }
    }
    menuFor?.let { song ->
        MediaContextMenu(
            header = ContextMenuHeader(title = song.title, subtitle = song.artist, coverArtId = song.coverArtId),
            actions = remember(song) {
                persistentListOf<MediaAction>().builder().apply {
                    add(MediaAction.PlayNext)
                    add(MediaAction.AddToQueue)
                    add(MediaAction.AddToPlaylist)
                    if (song.albumId != null) add(MediaAction.GoToAlbum)
                    if (song.artistId != null) add(MediaAction.GoToArtist)
                }.build()
            },
            onAction = { action ->
                when (action) {
                    MediaAction.PlayNext -> actions.onPlayNext(song)
                    MediaAction.AddToQueue -> actions.onAddToQueue(listOf(song))
                    MediaAction.AddToPlaylist -> actions.onAddToPlaylist(song)
                    MediaAction.GoToAlbum -> song.albumId?.let(actions.onAlbumClick)
                    MediaAction.GoToArtist -> song.artistId?.let(actions.onArtistClick)
                    else -> Unit
                }
            },
            onDismissRequest = { menuFor = null },
        )
    }
}

private fun Song.toTrackRow() = TrackRowState(
    id = id,
    title = title,
    artist = artist.orEmpty(),
    album = album,
    durationSeconds = durationSeconds.toLong(),
    coverArtId = coverArtId,
)
