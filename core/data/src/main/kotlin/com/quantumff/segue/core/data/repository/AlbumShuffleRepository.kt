package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.PlaylistDao
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.AlbumShuffleSource
import com.quantumff.segue.core.model.Song
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Reads the local library for "Shuffle albums". Which albums to pick and in what order is the
 * playback layer's policy. Reads only Room, so it works offline; empty when signed out.
 */
interface AlbumShuffleRepository {
    /** The albums [source] covers: the library, an artist's albums, albums with songs in a genre, or albums on a playlist. */
    suspend fun albumIds(source: AlbumShuffleSource): List<String>

    /**
     * The songs to queue for [albumIds] (any order). Whole albums, except for a playlist source,
     * which gives only the playlist's own tracks of those albums.
     */
    suspend fun songs(source: AlbumShuffleSource, albumIds: List<String>): List<Song>
}

internal class RoomAlbumShuffleRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : AlbumShuffleRepository {
    override suspend fun albumIds(source: AlbumShuffleSource): List<String> = withContext(ioDispatcher) {
        val serverId = clients.activeServerId.first() ?: return@withContext emptyList()
        when (source) {
            AlbumShuffleSource.Library -> albumDao.ids(serverId)

            is AlbumShuffleSource.Artist -> albumDao.observeByArtist(serverId, source.artistId).first().map { it.id }

            is AlbumShuffleSource.Genre -> songDao.albumIdsByGenre(serverId, source.name)

            is AlbumShuffleSource.Playlist ->
                playlistDao.observeSongs(serverId, source.playlistId).first().mapNotNull { it.albumId }.distinct()
        }
    }

    override suspend fun songs(source: AlbumShuffleSource, albumIds: List<String>): List<Song> =
        withContext(ioDispatcher) {
            val serverId = clients.activeServerId.first() ?: return@withContext emptyList()
            val songs = if (source is AlbumShuffleSource.Playlist) {
                val wanted = albumIds.toHashSet()
                playlistDao.observeSongs(serverId, source.playlistId).first().filter { it.albumId in wanted }
            } else {
                songDao.getByAlbumIds(serverId, albumIds)
            }
            songs.map { it.asExternalModel() }
        }
}
