package com.quantumff.segue.core.data.play

import com.quantumff.segue.core.data.getOrNull
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.model.ArtistAlbums
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.Song
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The songs a [PlayTarget] stands for, in play order: the one place that knows what playing an
 * album, a playlist or an artist means. Room first; songs not synced yet are fetched.
 */
class PlayTargetSongs @Inject constructor(
    private val albums: AlbumRepository,
    private val playlists: PlaylistRepository,
    private val artists: ArtistRepository,
) {
    /** Throws RemoteException when songs had to be fetched and the server failed. */
    suspend fun songsFor(target: PlayTarget): List<Song> = when (target) {
        is PlayTarget.Songs -> target.songs
        is PlayTarget.Album -> albums.songsForPlayback(target.albumId)
        is PlayTarget.Playlist -> playlists.songsForPlayback(target.playlistId)
        is PlayTarget.Artist -> artistSongs(target.artistId)
        is PlayTarget.Each -> target.targets.flatMap { songsFor(it) }
    }

    /** Top songs; without them (none, or no server) every album's songs in page order. */
    private suspend fun artistSongs(artistId: String): List<Song> {
        val top = remoteOutcome { artists.topSongs(artistId) }.getOrNull().orEmpty()
        if (top.isNotEmpty()) return top
        val pageOrder = ArtistAlbums.of(artistId, artists.artistAlbums(artistId).first()).inPageOrder
        return pageOrder.flatMap { albums.songsForPlayback(it.id) }
    }
}
