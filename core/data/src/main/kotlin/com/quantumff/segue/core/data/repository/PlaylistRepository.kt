package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.sync.LibrarySynchronizer
import com.quantumff.segue.core.data.sync.LibraryWriter
import com.quantumff.segue.core.database.dao.PlaylistDao
import com.quantumff.segue.core.database.model.PlaylistEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.network.SubsonicClient
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Playlists. Edits go to the server first, then the playlist is refetched into Room. All writes throw RemoteException. */
interface PlaylistRepository {
    val playlists: Flow<ImmutableList<Playlist>>

    fun playlist(id: String): Flow<Playlist?>

    /** In playlist order; a song added twice appears twice. */
    fun playlistSongs(id: String): Flow<ImmutableList<Song>>

    /** Refetches playlists whose `changed` moved and drops those deleted on the server. */
    suspend fun refreshPlaylists()

    suspend fun refreshPlaylist(id: String)

    /** Returns the new playlist's ID. */
    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): String

    suspend fun updatePlaylist(id: String, name: String? = null, comment: String? = null, isPublic: Boolean? = null)

    suspend fun addSongs(id: String, songIds: List<String>)

    /** [positions] are 0-based indexes into the playlist. */
    suspend fun removeSongs(id: String, positions: List<Int>)

    suspend fun deletePlaylist(id: String)
}

internal class OfflineFirstPlaylistRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val playlistDao: PlaylistDao,
    private val writer: LibraryWriter,
    private val synchronizer: LibrarySynchronizer,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : PlaylistRepository {
    override val playlists: Flow<ImmutableList<Playlist>> =
        clients.listForActiveServer(playlistDao::observeAll, PlaylistEntity::asExternalModel)

    override fun playlist(id: String): Flow<Playlist?> =
        clients.forActiveServer(null) { serverId -> playlistDao.observe(serverId, id).map { it?.asExternalModel() } }

    override fun playlistSongs(id: String): Flow<ImmutableList<Song>> =
        clients.listForActiveServer({ serverId -> playlistDao.observeSongs(serverId, id) }, SongEntity::asExternalModel)

    override suspend fun refreshPlaylists() {
        val serverId = clients.requireActiveServerId()
        remote { synchronizer.syncPlaylists(serverId) }
    }

    override suspend fun refreshPlaylist(id: String) = edit { serverId, client -> refetch(serverId, client, id) }

    override suspend fun createPlaylist(name: String, songIds: List<String>): String = edit { serverId, client ->
        val created = client.createPlaylist(name, songIds)
        if (created != null) {
            writer.writePlaylists(serverId, listOf(created))
            created.id
        } else {
            // Servers before API 1.14 do not return the playlist; find it by name among the newest.
            synchronizer.syncPlaylists(serverId)
            client.getPlaylists().filter { it.name == name }.maxByOrNull { it.created.orEmpty() }?.id
                ?: error("Created playlist '$name' not found")
        }
    }

    override suspend fun updatePlaylist(id: String, name: String?, comment: String?, isPublic: Boolean?) =
        edit { serverId, client ->
            client.updatePlaylist(id, name = name, comment = comment, isPublic = isPublic)
            refetch(serverId, client, id)
        }

    override suspend fun addSongs(id: String, songIds: List<String>) = edit { serverId, client ->
        client.updatePlaylist(id, songIdsToAdd = songIds)
        refetch(serverId, client, id)
    }

    override suspend fun removeSongs(id: String, positions: List<Int>) = edit { serverId, client ->
        client.updatePlaylist(id, songIndexesToRemove = positions.distinct().sorted())
        refetch(serverId, client, id)
    }

    override suspend fun deletePlaylist(id: String) = edit { serverId, client ->
        client.deletePlaylist(id)
        playlistDao.deleteByIds(serverId, listOf(id))
    }

    private suspend fun refetch(serverId: String, client: SubsonicClient, id: String) {
        writer.writePlaylists(serverId, listOf(client.getPlaylist(id)))
    }

    private suspend fun <T> edit(block: suspend (serverId: String, client: SubsonicClient) -> T): T =
        withContext(ioDispatcher) {
            val serverId = clients.requireActiveServerId()
            val client = clients.client(serverId)
            remote { block(serverId, client) }
        }
}
