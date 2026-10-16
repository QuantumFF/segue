package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.PlaylistDao
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.AlbumSource
import com.quantumff.segue.core.model.ArtistAlbums
import com.quantumff.segue.core.model.Song
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Reads the local library for "Play albums" and "Shuffle albums". Which albums to queue, in what
 * order and how many at a time is the playback layer's policy. Reads only Room, so it works
 * offline; empty when signed out.
 */
interface AlbumShuffleRepository {
    /**
     * The albums [source] covers: the library, a Library albums view (in its sort order), an
     * artist's albums (in the artist page's order), albums with songs in a genre, or albums on a playlist. IDs only, so even a
     * large library is cheap to list.
     */
    suspend fun albumIds(source: AlbumSource): List<String>

    /**
     * The songs to queue for [albumIds] (any order). Whole albums, except for a playlist source,
     * which gives only the playlist's own tracks of those albums.
     */
    suspend fun songs(source: AlbumSource, albumIds: List<String>): List<Song>
}

internal class RoomAlbumShuffleRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : AlbumShuffleRepository {
    override suspend fun albumIds(source: AlbumSource): List<String> = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext emptyList()
        when (source) {
            AlbumSource.Library -> albumDao.ids(serverId)

            is AlbumSource.Albums -> albumDao.idsInViewOrder(
                serverId = serverId,
                sort = source.sort.name,
                starredOnly = source.filter.starredOnly,
                genre = source.filter.genre,
                year = source.filter.year,
                downloadedOnly = source.filter.downloadedOnly,
            )

            // The artist page's order: their own albums, then the ones that only credit them.
            is AlbumSource.Artist -> {
                val albums = albumDao.observeByArtist(serverId, source.artistId).first().map { it.asExternalModel() }
                ArtistAlbums.of(source.artistId, albums).inPageOrder.map { it.id }
            }

            is AlbumSource.Genre -> songDao.albumIdsByGenre(serverId, source.name)

            is AlbumSource.Playlist ->
                playlistDao.observeSongs(serverId, source.playlistId).first().mapNotNull { it.albumId }.distinct()
        }
    }

    override suspend fun songs(source: AlbumSource, albumIds: List<String>): List<Song> = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext emptyList()
        val songs = if (source is AlbumSource.Playlist) {
            val wanted = albumIds.toHashSet()
            playlistDao.observeSongs(serverId, source.playlistId).first().filter { it.albumId in wanted }
        } else {
            songDao.getByAlbumIds(serverId, albumIds)
        }
        songs.map { it.asExternalModel() }
    }
}
