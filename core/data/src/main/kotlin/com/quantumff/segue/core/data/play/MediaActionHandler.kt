package com.quantumff.segue.core.data.play

import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.download.DownloadRepository
import com.quantumff.segue.core.data.download.PinTarget
import com.quantumff.segue.core.data.remoteOutcome
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.InstantMixSeed
import com.quantumff.segue.core.model.MediaAction
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PinKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import javax.inject.Inject

/**
 * Carries out the context-menu actions [com.quantumff.segue.core.model.MediaActions] offers, the
 * same on every screen: play intents go to [PlayIntents], Favorite stars, Download pins and Remove
 * download unpins. Navigation and the screen's own actions (Rename, Delete, Remove from playlist)
 * go to the [MediaActionHost] the screen passes in.
 *
 * Each call returns once the action is done, with the failure the screen should report, if any.
 * A play intent reports its own failures ([PlayIntents.messages]).
 */
class MediaActionHandler @Inject constructor(
    private val playIntents: PlayIntents,
    private val targets: PlayTargetSongs,
    private val downloads: DownloadRepository,
    private val stars: StarRatingRepository,
) {
    suspend fun album(album: Album, action: MediaAction, host: MediaActionHost): MediaActionFailure? = when (action) {
        MediaAction.ShuffleArtistAlbums -> album.artistId?.let { shuffleAlbums(AlbumSource.Artist(it)) }
        MediaAction.AddToPlaylist -> addToPlaylist(PlayTarget.Album(album.id), host)
        MediaAction.GoToArtist -> done { album.artistId?.let(host.onArtistClick) }
        MediaAction.Download -> pin(PinTarget.of(album))
        MediaAction.RemoveDownload -> unpin(PinKind.Album, album.id)
        else -> common(action, PlayTarget.Album(album.id), MediaKind.Album, album.id, host)
    }

    suspend fun song(song: Song, action: MediaAction, host: MediaActionHost): MediaActionFailure? = when (action) {
        MediaAction.InstantMix -> done { playIntents.instantMix(InstantMixSeed.Song(song)) }
        MediaAction.AddToPlaylist -> done { host.onAddToPlaylist(listOf(song.id)) }
        MediaAction.GoToAlbum -> done { song.albumId?.let(host.onAlbumClick) }
        MediaAction.GoToArtist -> done { song.artistId?.let(host.onArtistClick) }
        MediaAction.Download -> pin(PinTarget.of(song))
        MediaAction.RemoveDownload -> unpin(PinKind.Song, song.id)
        else -> common(action, PlayTarget.Songs(listOf(song)), MediaKind.Song, song.id, host)
    }

    suspend fun artist(artist: Artist, action: MediaAction, host: MediaActionHost): MediaActionFailure? =
        when (action) {
            MediaAction.ShuffleAlbums -> shuffleAlbums(AlbumSource.Artist(artist.id))
            MediaAction.Download -> pin(PinTarget.of(artist))
            MediaAction.RemoveDownload -> unpin(PinKind.Artist, artist.id)
            else -> common(action, PlayTarget.Artist(artist.id), MediaKind.Artist, artist.id, host)
        }

    suspend fun playlist(playlist: Playlist, action: MediaAction, host: MediaActionHost): MediaActionFailure? =
        when (action) {
            MediaAction.ShuffleAlbums -> shuffleAlbums(AlbumSource.Playlist(playlist.id))

            MediaAction.AddToPlaylist -> addToPlaylist(PlayTarget.Playlist(playlist.id), host)

            MediaAction.Download -> pin(PinTarget.of(playlist))

            MediaAction.RemoveDownload -> unpin(PinKind.Playlist, playlist.id)

            // Playlists are not starred: no MediaKind.
            else -> common(action, PlayTarget.Playlist(playlist.id), null, playlist.id, host)
        }

    /** The actions every kind handles alike: play intents, Favorite, and the screen's own. */
    private suspend fun common(
        action: MediaAction,
        target: PlayTarget,
        kind: MediaKind?,
        id: String,
        host: MediaActionHost,
    ): MediaActionFailure? = when (action) {
        MediaAction.Play -> done { playIntents.play(target) }

        MediaAction.Shuffle -> done { playIntents.play(target, shuffle = true) }

        MediaAction.PlayNext -> done { playIntents.play(target, QueuePlacement.Next) }

        MediaAction.AddToQueue -> done { playIntents.play(target, QueuePlacement.End) }

        MediaAction.Favorite, MediaAction.Unfavorite -> kind?.let {
            val starred = action == MediaAction.Favorite
            MediaActionFailure.FavoriteFailed.takeIf { remoteOutcome { stars.setStarred(kind, id, starred) }.isFailure }
        }

        MediaAction.Rename, MediaAction.Delete, MediaAction.RemoveFromPlaylist -> done { host.onScreenAction(action) }

        // Not offered for this kind.
        else -> null
    }

    private suspend fun shuffleAlbums(source: AlbumSource) =
        done { playIntents.playAlbums(AlbumPlayRequest(source, AlbumOrder.Shuffled)) }

    private suspend fun addToPlaylist(target: PlayTarget, host: MediaActionHost): MediaActionFailure? {
        val songs = try {
            targets.songsFor(target)
        } catch (_: RemoteException) {
            return MediaActionFailure.LoadFailed
        }
        if (songs.isNotEmpty()) host.onAddToPlaylist(songs.map { it.id })
        return null
    }

    private suspend fun pin(target: PinTarget) =
        MediaActionFailure.DownloadFailed.takeIf { remoteOutcome { downloads.pin(target) }.isFailure }

    private suspend fun unpin(kind: PinKind, id: String) = done { downloads.unpin(kind, id) }

    /** An action that cannot fail here: navigation, or a play intent, which reports its own failures. */
    private inline fun done(action: () -> Unit): MediaActionFailure? {
        action()
        return null
    }
}

/**
 * What a menu action needs from the screen it was raised on. Callbacks for actions a screen's
 * menus never offer can stay empty.
 */
class MediaActionHost(
    val onAlbumClick: (String) -> Unit = {},
    val onArtistClick: (String) -> Unit = {},
    /** Shows the add-to-playlist sheet for the song IDs. */
    val onAddToPlaylist: (List<String>) -> Unit = {},
    /** Rename, Delete and Remove from playlist: the screen's own. */
    val onScreenAction: (MediaAction) -> Unit = {},
)

/** A menu action that failed, for the screen's snackbar. */
enum class MediaActionFailure {
    /** The songs to add to a playlist could not be fetched. */
    LoadFailed,
    FavoriteFailed,
    DownloadFailed,
}
