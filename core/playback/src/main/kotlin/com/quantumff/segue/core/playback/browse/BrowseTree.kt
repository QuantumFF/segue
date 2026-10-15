package com.quantumff.segue.core.playback.browse

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.data.repository.MediaBrowseRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.shuffle.AlbumPicker
import kotlin.random.Random

/** Titles of the fixed nodes, from string resources. */
data class BrowseLabels(
    val home: String,
    val library: String,
    val downloads: String,
    val shuffleAlbums: String,
    val recentlyPlayed: String,
    val recentlyAdded: String,
    val starred: String,
    val albums: String,
    val artists: String,
    val playlists: String,
    val genres: String,
)

enum class BrowseIcon { Home, Library, Downloads, Shuffle }

/** Artwork URIs a media browser in another process can open (content and resource URIs). */
interface BrowseArtwork {
    fun cover(coverArtId: String): Uri

    fun icon(icon: BrowseIcon): Uri?
}

/** What to queue for a playable node: [songs] from [startIndex], with [shuffleMode] set first (null keeps the mode). */
data class PlayRequest(val songs: List<Song>, val startIndex: Int = 0, val shuffleMode: ShuffleMode? = null)

/**
 * The media browse tree for Android Auto and other media browsers, read from Room.
 *
 * ```
 * /                    Home · Library · Downloads (tabs)
 * /home                Shuffle albums (playable) · Recently played · Recently added · Starred
 * /library             Albums · Artists · Playlists · Genres
 * artists → albums, genres → albums, albums and playlists → tracks
 * ```
 *
 * Lists the database can page are read one page at a time; short lists (an artist's albums, an
 * album's tracks, playlists, genres) are read whole and sliced. Albums show as a grid, tracks and
 * everything else as a list.
 */
class BrowseTree(
    private val repository: MediaBrowseRepository,
    private val albumShuffle: AlbumShuffleRepository,
    private val labels: BrowseLabels,
    private val artwork: BrowseArtwork,
    private val random: Random = Random.Default,
) {
    fun root(): MediaItem = folder(BrowseId.Root, "Segue", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    /** One page of [parentId]'s children, or null when it is not a browsable node. */
    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem>? {
        val id = BrowseId.parse(parentId) ?: return null
        if (!id.isBrowsable()) return null
        if (page < 0 || pageSize <= 0) return emptyList()
        val offset = page * pageSize
        return when (id) {
            BrowseId.Root -> listOf(
                node(BrowseId.Home),
                node(BrowseId.Library),
                node(BrowseId.Downloads),
            ).slice(offset, pageSize)

            BrowseId.Home -> listOf(
                node(BrowseId.ShuffleAlbums),
                node(BrowseId.RecentlyPlayed),
                node(BrowseId.RecentlyAdded),
                node(BrowseId.Starred),
            ).slice(offset, pageSize)

            BrowseId.Library -> listOf(
                node(BrowseId.Albums),
                node(BrowseId.Artists),
                node(BrowseId.Playlists),
                node(BrowseId.Genres),
            ).slice(offset, pageSize)

            BrowseId.RecentlyPlayed -> repository.recentlyPlayedAlbums(offset, pageSize).map(::albumItem)

            BrowseId.RecentlyAdded -> repository.recentlyAddedAlbums(offset, pageSize).map(::albumItem)

            BrowseId.Starred -> repository.starredSongs(offset, pageSize).tracks(id, offset)

            BrowseId.Downloads -> repository.downloadedSongs(offset, pageSize).tracks(id, offset)

            BrowseId.Albums -> repository.albums(offset, pageSize).map(::albumItem)

            BrowseId.Artists -> repository.artists(offset, pageSize).map(::artistItem)

            BrowseId.Playlists -> repository.playlists().slice(offset, pageSize).map(::playlistItem)

            BrowseId.Genres -> repository.genres().slice(offset, pageSize).map(::genreItem)

            is BrowseId.Artist -> repository.artistAlbums(id.id).slice(offset, pageSize).map(::albumItem)

            is BrowseId.Genre -> repository.genreAlbums(id.name, offset, pageSize).map(::albumItem)

            is BrowseId.Album -> repository.albumSongs(id.id).slice(offset, pageSize).tracks(id, offset)

            is BrowseId.Playlist -> repository.playlistSongs(id.id).slice(offset, pageSize).tracks(id, offset)

            BrowseId.ShuffleAlbums, is BrowseId.Track -> null
        }
    }

    /** The node for [mediaId], or null if it does not exist (any more). */
    suspend fun item(mediaId: String): MediaItem? = when (val id = BrowseId.parse(mediaId)) {
        null -> null
        BrowseId.Root -> root()
        is BrowseId.Fixed -> node(id)
        is BrowseId.Album -> repository.album(id.id)?.let(::albumItem)
        is BrowseId.Artist -> repository.artist(id.id)?.let(::artistItem)
        is BrowseId.Playlist -> repository.playlist(id.id)?.let(::playlistItem)
        is BrowseId.Genre -> repository.genres().firstOrNull { it.name == id.name }?.let(::genreItem)
        is BrowseId.Track -> repository.song(id.songId)?.let { trackItem(it, id) }
    }

    /** What playing [mediaId] queues, or null if it is not playable or there is nothing to play. */
    suspend fun playRequest(mediaId: String): PlayRequest? {
        val request = when (val id = BrowseId.parse(mediaId)) {
            BrowseId.ShuffleAlbums -> shuffleAlbums()
            is BrowseId.Album -> PlayRequest(repository.albumSongsForPlayback(id.id))
            is BrowseId.Playlist -> PlayRequest(repository.playlistSongs(id.id))
            is BrowseId.Track -> trackRequest(id)
            else -> null
        }
        return request?.takeIf { it.songs.isNotEmpty() }
    }

    private suspend fun trackRequest(id: BrowseId.Track): PlayRequest? {
        val list = when (val parent = id.parent) {
            is BrowseId.Album -> repository.albumSongsForPlayback(parent.id)
            is BrowseId.Playlist -> repository.playlistSongs(parent.id)
            BrowseId.Starred -> repository.starredSongs(0, MAX_QUEUE)
            BrowseId.Downloads -> repository.downloadedSongs(0, MAX_QUEUE)
            else -> emptyList()
        }
        // The list may have changed since it was browsed: find the song again if it moved.
        val start = id.index.takeIf { list.getOrNull(it)?.id == id.songId } ?: list.indexOfFirst { it.id == id.songId }
        if (start >= 0) return PlayRequest(list, start)
        return repository.song(id.songId)?.let { PlayRequest(listOf(it)) }
    }

    /** Same as the app's "Shuffle albums" for the library: random whole albums in plain order. */
    private suspend fun shuffleAlbums(): PlayRequest {
        val picked = AlbumPicker.pick(albumShuffle.albumIds(AlbumShuffleSource.Library), AlbumPicker.DEFAULT_ALBUM_COUNT, random)
        val songs = AlbumPicker.queueInAlbumOrder(albumShuffle.songs(AlbumShuffleSource.Library, picked), picked)
        return PlayRequest(songs, startIndex = 0, shuffleMode = ShuffleMode.Off)
    }

    // --- Items -----------------------------------------------------------------------------------

    private fun node(id: BrowseId.Fixed): MediaItem = when (id) {
        BrowseId.Root -> root()

        BrowseId.Home -> folder(id, labels.home, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, icon = BrowseIcon.Home)

        BrowseId.Library -> folder(id, labels.library, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, icon = BrowseIcon.Library)

        BrowseId.Downloads -> folder(
            id,
            labels.downloads,
            MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            icon = BrowseIcon.Downloads,
            playableStyle = LIST,
        )

        BrowseId.ShuffleAlbums -> MediaItem.Builder()
            .setMediaId(id.encode())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(labels.shuffleAlbums)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MIXED)
                    .setArtworkUri(artwork.icon(BrowseIcon.Shuffle))
                    .build(),
            )
            .build()

        BrowseId.RecentlyPlayed ->
            folder(id, labels.recentlyPlayed, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, browsableStyle = GRID)

        BrowseId.RecentlyAdded ->
            folder(id, labels.recentlyAdded, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, browsableStyle = GRID)

        BrowseId.Starred -> folder(id, labels.starred, MediaMetadata.MEDIA_TYPE_PLAYLIST, playableStyle = LIST)

        BrowseId.Albums -> folder(id, labels.albums, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, browsableStyle = GRID)

        BrowseId.Artists -> folder(id, labels.artists, MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)

        BrowseId.Playlists -> folder(id, labels.playlists, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)

        BrowseId.Genres -> folder(id, labels.genres, MediaMetadata.MEDIA_TYPE_FOLDER_GENRES)
    }

    private fun folder(
        id: BrowseId,
        title: String,
        mediaType: Int,
        icon: BrowseIcon? = null,
        browsableStyle: Int = LIST,
        playableStyle: Int = LIST,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id.encode())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .setArtworkUri(icon?.let(artwork::icon))
                .setExtras(styles(browsableStyle, playableStyle))
                .build(),
        )
        .build()

    internal fun albumItem(album: Album): MediaItem = MediaItem.Builder()
        .setMediaId(BrowseId.Album(album.id).encode())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(album.name)
                .setArtist(album.artist)
                .setAlbumTitle(album.name)
                .setAlbumArtist(album.artist)
                .setReleaseYear(album.year)
                .setArtworkUri(album.coverArtId?.let(artwork::cover))
                .setIsBrowsable(true)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                .setExtras(styles(browsable = LIST, playable = LIST))
                .build(),
        )
        .build()

    internal fun artistItem(artist: Artist): MediaItem = MediaItem.Builder()
        .setMediaId(BrowseId.Artist(artist.id).encode())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(artist.name)
                .setArtist(artist.name)
                .setArtworkUri(artist.coverArtId?.let(artwork::cover))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                .setExtras(styles(browsable = GRID, playable = LIST))
                .build(),
        )
        .build()

    private fun playlistItem(playlist: Playlist): MediaItem = MediaItem.Builder()
        .setMediaId(BrowseId.Playlist(playlist.id).encode())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(playlist.name)
                .setArtworkUri(playlist.coverArtId?.let(artwork::cover))
                .setIsBrowsable(true)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .setExtras(styles(browsable = LIST, playable = LIST))
                .build(),
        )
        .build()

    private fun genreItem(genre: Genre): MediaItem = MediaItem.Builder()
        .setMediaId(BrowseId.Genre(genre.name).encode())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(genre.name)
                .setGenre(genre.name)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_GENRE)
                .setExtras(styles(browsable = GRID, playable = LIST))
                .build(),
        )
        .build()

    /** A track in a list: a list row with small art and duration. */
    fun trackItem(song: Song, id: BrowseId.Track): MediaItem = MediaItem.Builder()
        .setMediaId(id.encode())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setDurationMs(song.durationSeconds.takeIf { it > 0 }?.let { it * MILLIS_PER_SECOND })
                .setArtworkUri(song.coverArtId?.let(artwork::cover))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

    private fun List<Song>.tracks(parent: BrowseId, offset: Int) =
        mapIndexed { i, song -> trackItem(song, BrowseId.Track(song.id, parent, offset + i)) }

    private fun <T> List<T>.slice(offset: Int, limit: Int): List<T> = drop(offset).take(limit)

    private fun BrowseId.isBrowsable() = this !is BrowseId.Track && this != BrowseId.ShuffleAlbums

    companion object {
        private const val GRID = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        private const val LIST = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        private const val MILLIS_PER_SECOND = 1_000L

        /** Upper bound for a queue built from a long list (starred songs, downloads). */
        const val MAX_QUEUE = 500

        /**
         * Root extras: children default to list rows. Media3 adds "search supported" itself when
         * the browser may search.
         */
        fun rootExtras(): Bundle = styles(LIST, LIST)

        /** How a node's browsable and playable children are drawn. */
        private fun styles(browsable: Int, playable: Int) = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, browsable)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, playable)
        }
    }
}
