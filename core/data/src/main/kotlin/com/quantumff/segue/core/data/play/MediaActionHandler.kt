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
import com.quantumff.segue.core.model.MediaActionFailure
import com.quantumff.segue.core.model.MediaKind
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
        MediaAction.GoToArtist -> done { album.artistId?.let(host.onArtistClick) }
        else -> common(action, Subject.of(album), host)
    }

    suspend fun song(song: Song, action: MediaAction, host: MediaActionHost): MediaActionFailure? = when (action) {
        MediaAction.InstantMix -> done { playIntents.instantMix(InstantMixSeed.Song(song)) }
        MediaAction.GoToAlbum -> done { song.albumId?.let(host.onAlbumClick) }
        MediaAction.GoToArtist -> done { song.artistId?.let(host.onArtistClick) }
        else -> common(action, Subject.of(song), host)
    }

    suspend fun artist(artist: Artist, action: MediaAction, host: MediaActionHost): MediaActionFailure? =
        common(action, Subject.of(artist), host)

    suspend fun playlist(playlist: Playlist, action: MediaAction, host: MediaActionHost): MediaActionFailure? =
        common(action, Subject.of(playlist), host)

    /** The actions every kind handles alike: play intents, Add to playlist, Favorite, downloads, the screen's own. */
    private suspend fun common(action: MediaAction, subject: Subject, host: MediaActionHost): MediaActionFailure? =
        when (action) {
            MediaAction.Play -> done { playIntents.play(subject.target) }

            MediaAction.Shuffle -> done { playIntents.play(subject.target, shuffle = true) }

            MediaAction.ShuffleAlbums -> subject.albums?.let { shuffleAlbums(it) }

            MediaAction.PlayNext -> done { playIntents.play(subject.target, QueuePlacement.Next) }

            MediaAction.AddToQueue -> done { playIntents.play(subject.target, QueuePlacement.End) }

            MediaAction.AddToPlaylist -> addToPlaylist(subject.target, host)

            MediaAction.Favorite, MediaAction.Unfavorite -> subject.starKind?.let { kind ->
                val starred = action == MediaAction.Favorite
                val outcome = remoteOutcome { stars.setStarred(kind, subject.pin.id, starred) }
                MediaActionFailure.FavoriteFailed.takeIf { outcome.isFailure }
            }

            MediaAction.Download ->
                MediaActionFailure.DownloadFailed.takeIf { remoteOutcome { downloads.pin(subject.pin) }.isFailure }

            MediaAction.RemoveDownload -> done { downloads.unpin(subject.pin.kind, subject.pin.id) }

            MediaAction.Rename, MediaAction.Delete, MediaAction.RemoveFromPlaylist ->
                done { host.onScreenAction(action) }

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
        if (songs.isEmpty()) return MediaActionFailure.NothingToAdd
        host.onAddToPlaylist(songs.map { it.id })
        return null
    }

    /** An action that cannot fail here: navigation, or a play intent, which reports its own failures. */
    private inline fun done(action: () -> Unit): MediaActionFailure? {
        action()
        return null
    }

    /** What the shared actions need to know about the album, song, artist or playlist a menu is for. */
    private class Subject(
        /** What Play, Shuffle, Play next, Add to queue and Add to playlist take the songs of. */
        val target: PlayTarget,
        /** What Download pins and Remove download unpins; its ID is also the one starred. */
        val pin: PinTarget,
        /** Null for playlists, which are not starred. */
        val starKind: MediaKind?,
        /** Where Shuffle albums takes its albums from; null on menus without it. */
        val albums: AlbumSource? = null,
    ) {
        companion object {
            fun of(album: Album) = Subject(PlayTarget.Album(album.id), PinTarget.of(album), MediaKind.Album)

            fun of(song: Song) = Subject(PlayTarget.Songs(listOf(song)), PinTarget.of(song), MediaKind.Song)

            fun of(artist: Artist) = Subject(
                PlayTarget.Artist(artist.id),
                PinTarget.of(artist),
                MediaKind.Artist,
                albums = AlbumSource.Artist(artist.id),
            )

            fun of(playlist: Playlist) = Subject(
                PlayTarget.Playlist(playlist.id),
                PinTarget.of(playlist),
                starKind = null,
                albums = AlbumSource.Playlist(playlist.id),
            )
        }
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
