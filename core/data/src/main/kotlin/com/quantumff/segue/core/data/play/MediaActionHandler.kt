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
import com.quantumff.segue.core.model.MediaActions
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.PlayTarget
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.Song
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Carries out the context-menu actions [MediaActions] offers, the same on every screen, for one
 * item or a multi-selection: play intents go to [PlayIntents], Favorite stars, Download pins and Remove
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
        MediaAction.InstantMix -> done { playIntents.instantMix(InstantMixSeed.OfSong(song)) }
        MediaAction.GoToAlbum -> done { song.albumId?.let(host.onAlbumClick) }
        MediaAction.GoToArtist -> done { song.artistId?.let(host.onArtistClick) }
        else -> common(action, Subject.of(song), host)
    }

    suspend fun artist(artist: Artist, action: MediaAction, host: MediaActionHost): MediaActionFailure? =
        common(action, Subject.of(artist), host)

    suspend fun playlist(playlist: Playlist, action: MediaAction, host: MediaActionHost): MediaActionFailure? =
        common(action, Subject.of(playlist), host)

    /**
     * A multi-selection's action ([MediaActions.selection]) on all of [albums], queued in the
     * order given. Shuffle albums queues them whole in a fresh album order.
     */
    suspend fun albums(albums: List<Album>, action: MediaAction, host: MediaActionHost): MediaActionFailure? {
        fun target(albums: List<Album>) = PlayTarget.Each(albums.map { PlayTarget.Album(it.id) })
        return when (action) {
            MediaAction.ShuffleAlbums -> done { playIntents.play(target(albums.shuffled())) }
            else -> several(action, target(albums), albums.map(Subject::of), host)
        }
    }

    /** A multi-selection's action on all of [artists]: each plays as on their own menu, in the order given. */
    suspend fun artists(artists: List<Artist>, action: MediaAction, host: MediaActionHost): MediaActionFailure? {
        val target = PlayTarget.Each(artists.map { PlayTarget.Artist(it.id) })
        return several(action, target, artists.map(Subject::of), host)
    }

    /** A multi-selection's action on all of [songs], queued in the order given. */
    suspend fun songs(songs: List<Song>, action: MediaAction, host: MediaActionHost): MediaActionFailure? =
        several(action, PlayTarget.Songs(songs), songs.map(Subject::of), host)

    /** The actions every kind handles alike: play intents, Add to playlist, Favorite, downloads, the screen's own. */
    private suspend fun common(action: MediaAction, subject: Subject, host: MediaActionHost): MediaActionFailure? =
        when (action) {
            MediaAction.Play, MediaAction.Shuffle, MediaAction.PlayNext, MediaAction.AddToQueue,
            MediaAction.AddToPlaylist,
            -> songsAction(action, subject.target, host)

            MediaAction.ShuffleAlbums -> subject.albums?.let { shuffleAlbums(it) }

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

    /**
     * The shared actions on several subjects at once: [target] stands for all their songs, and
     * Favorite and Download leave alone the ones already starred or pinned. One failure does not
     * stop the rest.
     */
    private suspend fun several(
        action: MediaAction,
        target: PlayTarget,
        subjects: List<Subject>,
        host: MediaActionHost,
    ): MediaActionFailure? = when (action) {
        MediaAction.Play, MediaAction.Shuffle, MediaAction.PlayNext, MediaAction.AddToQueue,
        MediaAction.AddToPlaylist,
        -> songsAction(action, target, host)

        MediaAction.Favorite, MediaAction.Unfavorite -> {
            val starred = action == MediaAction.Favorite
            val failures = subjects.filter { it.isStarred != starred }.count { subject ->
                val kind = subject.starKind ?: return@count false
                remoteOutcome { stars.setStarred(kind, subject.pin.id, starred) }.isFailure
            }
            MediaActionFailure.FavoriteFailed.takeIf { failures > 0 }
        }

        MediaAction.Download -> {
            val unpinned = subjects.filter { downloads.pinStatus(it.pin.kind, it.pin.id).first() == null }
            val failures = unpinned.count { remoteOutcome { downloads.pin(it.pin) }.isFailure }
            MediaActionFailure.DownloadFailed.takeIf { failures > 0 }
        }

        MediaAction.RemoveDownload -> done { subjects.forEach { downloads.unpin(it.pin.kind, it.pin.id) } }

        // Not offered for a selection.
        else -> null
    }

    /** The actions that take [target]'s songs: Play, Shuffle, Play next, Add to queue, Add to playlist. */
    private suspend fun songsAction(action: MediaAction, target: PlayTarget, host: MediaActionHost) = when (action) {
        MediaAction.Play -> done { playIntents.play(target) }
        MediaAction.Shuffle -> done { playIntents.play(target, shuffle = true) }
        MediaAction.PlayNext -> done { playIntents.play(target, QueuePlacement.Next) }
        MediaAction.AddToQueue -> done { playIntents.play(target, QueuePlacement.End) }
        MediaAction.AddToPlaylist -> addToPlaylist(target, host)
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
        /** Starred already, so a selection's Favorite skips it. */
        val isStarred: Boolean,
        /** Where Shuffle albums takes its albums from; null on menus without it. */
        val albums: AlbumSource? = null,
    ) {
        companion object {
            fun of(album: Album) =
                Subject(PlayTarget.Album(album.id), PinTarget.of(album), MediaKind.Album, album.isStarred)

            fun of(song: Song) =
                Subject(PlayTarget.Songs(listOf(song)), PinTarget.of(song), MediaKind.Song, song.isStarred)

            fun of(artist: Artist) = Subject(
                PlayTarget.Artist(artist.id),
                PinTarget.of(artist),
                MediaKind.Artist,
                artist.isStarred,
                albums = AlbumSource.Artist(artist.id),
            )

            fun of(playlist: Playlist) = Subject(
                PlayTarget.Playlist(playlist.id),
                PinTarget.of(playlist),
                starKind = null,
                isStarred = false,
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
