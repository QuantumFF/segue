package com.quantumff.segue.core.data.repository

import androidx.paging.PagingData
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.model.asEntity
import com.quantumff.segue.core.data.remote
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import com.quantumff.segue.core.data.session.requireActiveServerId
import com.quantumff.segue.core.data.sync.LibraryWriter
import com.quantumff.segue.core.database.TransactionRunner
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.ArtistDao
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.ArtistEntity
import com.quantumff.segue.core.database.model.asExternalModel
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ArtistInfo
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.network.model.NetworkChild
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface ArtistRepository {
    fun artists(): Flow<PagingData<Artist>>

    val artistCount: Flow<Int>

    fun artist(id: String): Flow<Artist?>

    /** Newest first, including albums that credit the artist only in OpenSubsonic `artists`. */
    fun artistAlbums(id: String): Flow<ImmutableList<Album>>

    val starredArtists: Flow<ImmutableList<Artist>>

    /** Fetches the artist and its album list. Throws RemoteException. */
    suspend fun refreshArtist(id: String)

    /**
     * The server's top songs for the artist, for one-off playback. The songs are also stored in
     * Room. Throws RemoteException.
     */
    suspend fun topSongs(artistId: String, count: Int = DEFAULT_SONG_COUNT): ImmutableList<Song>

    /** Songs similar to the artist (`getSimilarSongs2`), for an instant mix. Throws RemoteException. */
    suspend fun similarSongs(artistId: String, count: Int = DEFAULT_SONG_COUNT): ImmutableList<Song>

    /** Biography and similar artists (`getArtistInfo2`). Not cached. Throws RemoteException. */
    suspend fun artistInfo(artistId: String, similarCount: Int = DEFAULT_SIMILAR_COUNT): ArtistInfo

    companion object {
        const val DEFAULT_SONG_COUNT = 50
        const val DEFAULT_SIMILAR_COUNT = 20
    }
}

internal class OfflineFirstArtistRepository @Inject constructor(
    private val clients: SubsonicClientProvider,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val capabilities: CachedServerCapabilitiesRepository,
    private val writer: LibraryWriter,
    private val transactions: TransactionRunner,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ArtistRepository {
    override fun artists(): Flow<PagingData<Artist>> =
        clients.pagedForActiveServer(artistDao::pagingSource, ArtistEntity::asExternalModel)

    override val artistCount: Flow<Int> = clients.forActiveServer(0, artistDao::observeCount)

    override fun artist(id: String): Flow<Artist?> =
        clients.forActiveServer(null) { serverId -> artistDao.observe(serverId, id).map { it?.asExternalModel() } }

    override fun artistAlbums(id: String): Flow<ImmutableList<Album>> = clients.listForActiveServer({ serverId ->
        albumDao.observeByArtist(serverId, id)
    }, AlbumEntity::asExternalModel)

    override val starredArtists: Flow<ImmutableList<Artist>> =
        clients.listForActiveServer(artistDao::observeStarred, ArtistEntity::asExternalModel)

    override suspend fun refreshArtist(id: String) = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val artist = remote { clients.client(serverId).getArtist(id) }
        transactions.run {
            artistDao.upsertAll(listOf(artist.asEntity(serverId)))
            writer.writeAlbumSummaries(serverId, artist.album)
        }
    }

    override suspend fun topSongs(artistId: String, count: Int): ImmutableList<Song> = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val name = artistDao.observe(serverId, artistId).first()?.name ?: return@withContext emptySongs()
        val byId = artistId.takeIf { capabilities.forServer(serverId).supportsTopSongsByArtistId }
        storeAndMap(serverId, remote { clients.client(serverId).getTopSongs(name, byId, count) })
    }

    override suspend fun similarSongs(artistId: String, count: Int): ImmutableList<Song> = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        storeAndMap(serverId, remote { clients.client(serverId).getSimilarSongs2(artistId, count) })
    }

    override suspend fun artistInfo(artistId: String, similarCount: Int): ArtistInfo = withContext(ioDispatcher) {
        val serverId = clients.requireActiveServerId()
        val info = remote { clients.client(serverId).getArtistInfo2(artistId, similarCount) }
        // Similar artists may be missing from the synced index, for example after a server-side rescan.
        val known = artistDao.observeAll(serverId).first().associateBy { it.id }
        ArtistInfo(
            biography = info.biography?.let(::plainBiography)?.takeIf { it.isNotBlank() },
            similarArtists = info.similarArtist
                .map { known[it.id]?.asExternalModel() ?: it.asEntity(serverId).asExternalModel() }
                .toImmutableList(),
        )
    }

    private suspend fun storeAndMap(serverId: String, songs: List<NetworkChild>): ImmutableList<Song> {
        writer.writeSongs(serverId, songs)
        return songs.filterNot { it.isDir }.map { it.asEntity(serverId).asExternalModel() }.toImmutableList()
    }

    private fun emptySongs(): ImmutableList<Song> = emptyList<Song>().toImmutableList()
}

/** Strips HTML tags, entities and the Last.fm "Read more on Last.fm" link from a biography. */
internal fun plainBiography(html: String): String = html
    .replace(Regex("<a [^>]*>.*?</a>\\.?", RegexOption.IGNORE_CASE), "")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()
