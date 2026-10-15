package com.quantumff.segue.feature.home

import com.quantumff.segue.core.designsystem.component.MediaAction
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.PlayActions
import com.quantumff.segue.core.model.Song

/**
 * Carries out an album context-menu [action]. [withSongs] loads the album's songs (fetching them
 * when they are not synced yet) before a play intent.
 */
@Suppress("LongParameterList")
internal fun dispatchAlbumAction(
    album: Album,
    action: MediaAction,
    playActions: PlayActions,
    withSongs: (Album, (List<Song>) -> Unit) -> Unit,
    onToggleFavorite: (Album) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
) {
    when (action) {
        MediaAction.Play -> withSongs(album) { playActions.onPlaySongs(it, 0, false) }

        MediaAction.Shuffle -> withSongs(album) { playActions.onPlaySongs(it, 0, true) }

        MediaAction.PlayNext -> withSongs(album) { playActions.onPlayNext(it) }

        MediaAction.AddToQueue -> withSongs(album) { playActions.onAddToQueue(it) }

        MediaAction.AddToPlaylist -> withSongs(album) { songs -> onAddToPlaylist(songs.map { it.id }) }

        MediaAction.ShuffleArtistAlbums ->
            album.artistId?.let { playActions.onShuffleAlbums(AlbumShuffleSource.Artist(it)) }

        MediaAction.Favorite, MediaAction.Unfavorite -> onToggleFavorite(album)

        MediaAction.GoToArtist -> album.artistId?.let(onArtistClick)

        // TODO(downloads): pin the album for offline playback once downloads exist.
        MediaAction.Download -> Unit

        else -> Unit
    }
}
