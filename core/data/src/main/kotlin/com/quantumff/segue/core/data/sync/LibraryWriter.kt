package com.quantumff.segue.core.data.sync

import com.quantumff.segue.core.data.model.artistCrossRefs
import com.quantumff.segue.core.data.model.asEntity
import com.quantumff.segue.core.data.model.genreCrossRefs
import com.quantumff.segue.core.data.model.songEntities
import com.quantumff.segue.core.database.TransactionRunner
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.PlaylistDao
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.network.model.NetworkAlbum
import com.quantumff.segue.core.network.model.NetworkChild
import com.quantumff.segue.core.network.model.NetworkPlaylist
import javax.inject.Inject

/** Writes network results into Room, one transaction per call. Shared by sync and refreshes. */
internal class LibraryWriter @Inject constructor(
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val transactions: TransactionRunner,
) {
    /**
     * Stores albums from `getAlbum` with their complete track lists, and deletes [missing] albums
     * (listed earlier but gone by the time they were fetched).
     */
    suspend fun writeAlbums(serverId: String, albums: List<NetworkAlbum>, missing: Collection<String> = emptyList()) {
        val ids = albums.map { it.id }
        transactions.run {
            albumDao.upsertAll(albums.map { it.asEntity(serverId) })
            albumDao.replaceAlbumArtists(serverId, ids, albums.flatMap { it.artistCrossRefs(serverId) })
            albumDao.replaceAlbumGenres(serverId, ids, albums.flatMap { it.genreCrossRefs(serverId) })
            songDao.replaceSongsOfAlbums(serverId, ids, albums.flatMap { it.song.songEntities(serverId) })
            albumDao.deleteByIds(serverId, missing)
        }
    }

    /** Stores albums from a list endpoint. Their songs are left alone. */
    suspend fun writeAlbumSummaries(serverId: String, albums: List<NetworkAlbum>) {
        albumDao.upsertAll(albums.map { it.asEntity(serverId) })
    }

    /** Stores playlists from `getPlaylist`, with their entries as songs. */
    suspend fun writePlaylists(serverId: String, playlists: List<NetworkPlaylist>) {
        transactions.run {
            playlists.forEach { playlist ->
                val songs = playlist.entry.songEntities(serverId)
                songDao.upsertAll(songs)
                playlistDao.replacePlaylist(playlist.asEntity(serverId), songs.map { it.id })
            }
        }
    }

    suspend fun writeSongs(serverId: String, songs: List<NetworkChild>) {
        songDao.upsertAll(songs.songEntities(serverId))
    }
}
