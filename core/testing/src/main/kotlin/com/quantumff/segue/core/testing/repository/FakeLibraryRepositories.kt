package com.quantumff.segue.core.testing.repository

import androidx.paging.PagingData
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.AlbumRepository
import com.quantumff.segue.core.data.repository.ArtistRepository
import com.quantumff.segue.core.data.repository.LibraryRepository
import com.quantumff.segue.core.data.repository.LibrarySyncRepository
import com.quantumff.segue.core.data.repository.SearchRepository
import com.quantumff.segue.core.data.repository.StarRatingRepository
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.AlbumFilter
import com.quantumff.segue.core.model.AlbumListType
import com.quantumff.segue.core.model.AlbumSortOrder
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ArtistFilter
import com.quantumff.segue.core.model.ArtistInfo
import com.quantumff.segue.core.model.ArtistSortOrder
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.MediaKind
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SyncProgress
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// In-memory fakes of the core:data repositories for ViewModel tests. State lives in public
// MutableStateFlows so tests can seed and change it; calls are recorded for assertions.

class FakeAlbumRepository : AlbumRepository {
    val albumsState = MutableStateFlow<List<Album>>(emptyList())
    val songsByAlbum = MutableStateFlow<Map<String, List<Song>>>(emptyMap())
    val lists = MutableStateFlow<Map<AlbumListType, List<Album>>>(emptyMap())
    val refreshedAlbums = mutableListOf<String>()
    val refreshedLists = mutableListOf<AlbumListType>()

    /** Thrown by refresh calls when set. */
    var refreshError: RemoteException? = null

    /** Albums that count as downloaded for [AlbumFilter.downloaded]. */
    var downloadedAlbumIds: Set<String> = emptySet()

    /** The last filter [albums] was called with. */
    var lastFilter: AlbumFilter? = null

    override fun albums(sort: AlbumSortOrder, filter: AlbumFilter): Flow<PagingData<Album>> {
        lastFilter = filter
        return albumsState.map { all -> PagingData.from(all.filter { matches(filter, it) }) }
    }

    override fun albumCount(filter: AlbumFilter): Flow<Int> = albumsState.map { all ->
        all.count { matches(filter, it) }
    }

    private fun matches(filter: AlbumFilter, album: Album) =
        filter.matches(album) && (filter.downloaded == null || (album.id in downloadedAlbumIds) == filter.downloaded)

    override val albumYears: Flow<ImmutableList<Int>> =
        albumsState.map { all -> all.mapNotNull { it.year }.distinct().sortedDescending().toImmutableList() }

    override fun album(id: String): Flow<Album?> = albumsState.map { all -> all.firstOrNull { it.id == id } }

    override fun albumSongs(id: String): Flow<ImmutableList<Song>> =
        songsByAlbum.map { it[id].orEmpty().toImmutableList() }

    override fun albumList(type: AlbumListType): Flow<ImmutableList<Album>> =
        lists.map { it[type].orEmpty().toImmutableList() }

    override val starredAlbums: Flow<ImmutableList<Album>> =
        albumsState.map { all -> all.filter { it.isStarred }.toImmutableList() }

    override suspend fun refreshAlbum(id: String) {
        refreshedAlbums += id
        refreshError?.let { throw it }
    }

    override suspend fun songsForPlayback(id: String): ImmutableList<Song> {
        refreshError?.let { throw it }
        return songsByAlbum.value[id].orEmpty().toImmutableList()
    }

    override suspend fun refreshAlbumList(type: AlbumListType, size: Int) {
        refreshedLists += type
        refreshError?.let { throw it }
    }
}

private fun AlbumFilter.matches(album: Album): Boolean = (starred == null || album.isStarred == starred) &&
    (genre == null || album.genre == genre) &&
    (year == null || album.year == year)

class FakeArtistRepository : ArtistRepository {
    val artistsState = MutableStateFlow<List<Artist>>(emptyList())
    val albumsByArtist = MutableStateFlow<Map<String, List<Album>>>(emptyMap())
    var topSongs: List<Song> = emptyList()
    var similarSongs: List<Song> = emptyList()
    var info = ArtistInfo(biography = null, similarArtists = persistentListOf())
    var error: RemoteException? = null
    val refreshed = mutableListOf<String>()

    /** Artists that count as downloaded for [ArtistFilter.downloaded]. */
    var downloadedArtistIds: Set<String> = emptySet()

    /** The last sort [artists] was called with. Artists come back unsorted. */
    var lastSort: ArtistSortOrder? = null

    /** The last filter [artists] was called with. */
    var lastFilter: ArtistFilter? = null

    override fun artists(sort: ArtistSortOrder, filter: ArtistFilter): Flow<PagingData<Artist>> {
        lastSort = sort
        lastFilter = filter
        return artistsState.map { all -> PagingData.from(all.filter { matches(filter, it) }) }
    }

    override fun artistCount(filter: ArtistFilter): Flow<Int> = artistsState.map { all ->
        all.count { matches(filter, it) }
    }

    private fun matches(filter: ArtistFilter, artist: Artist) =
        (filter.starred == null || artist.isStarred == filter.starred) &&
            (filter.downloaded == null || (artist.id in downloadedArtistIds) == filter.downloaded)

    override fun artist(id: String): Flow<Artist?> = artistsState.map { all -> all.firstOrNull { it.id == id } }

    override fun artistAlbums(id: String): Flow<ImmutableList<Album>> =
        albumsByArtist.map { it[id].orEmpty().toImmutableList() }

    override val starredArtists: Flow<ImmutableList<Artist>> =
        artistsState.map { all -> all.filter { it.isStarred }.toImmutableList() }

    override suspend fun refreshArtist(id: String) {
        refreshed += id
        error?.let { throw it }
    }

    override suspend fun topSongs(artistId: String, count: Int): ImmutableList<Song> {
        error?.let { throw it }
        return topSongs.take(count).toImmutableList()
    }

    override suspend fun similarSongs(artistId: String, count: Int): ImmutableList<Song> {
        error?.let { throw it }
        return similarSongs.take(count).toImmutableList()
    }

    override suspend fun artistInfo(artistId: String, similarCount: Int): ArtistInfo {
        error?.let { throw it }
        return info
    }
}

class FakeLibraryRepository : LibraryRepository {
    val genresState = MutableStateFlow<List<Genre>>(emptyList())
    val songsState = MutableStateFlow<List<Song>>(emptyList())

    override val genres: Flow<ImmutableList<Genre>> = genresState.map { it.toImmutableList() }

    override fun songs(): Flow<PagingData<Song>> = songsState.map { PagingData.from(it) }

    override val songCount: Flow<Int> = songsState.map { it.size }

    /** Songs [downloadedSongs] returns. */
    val downloadedSongsState = MutableStateFlow<List<Song>>(emptyList())

    override fun downloadedSongs(): Flow<PagingData<Song>> = downloadedSongsState.map { PagingData.from(it) }

    override val downloadedSongCount: Flow<Int> = downloadedSongsState.map { it.size }

    override fun songsByGenre(genre: String): Flow<PagingData<Song>> =
        songsState.map { all -> PagingData.from(all.filter { it.genre == genre }) }

    override fun song(id: String): Flow<Song?> = songsState.map { all -> all.firstOrNull { it.id == id } }

    override val starredSongs: Flow<ImmutableList<Song>> =
        songsState.map { all -> all.filter { it.isStarred }.toImmutableList() }

    override suspend fun songsByIds(ids: List<String>): ImmutableList<Song> {
        val byId = songsState.value.associateBy { it.id }
        return ids.mapNotNull { byId[it] }.toImmutableList()
    }
}

class FakeLibrarySyncRepository : LibrarySyncRepository {
    override val progress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    val requests = mutableListOf<Boolean>()

    override suspend fun requestSync(fullRefresh: Boolean) {
        requests += fullRefresh
    }
}

class FakeSearchRepository : SearchRepository {
    /** Emissions per query; queries without an entry emit [SearchResult.Empty]. */
    val results = mutableMapOf<String, List<SearchResult>>()
    val queries = mutableListOf<String>()
    val localQueries = mutableListOf<String>()

    /** Local-only results per query; falls back to the first emission in [results]. */
    val localResults = mutableMapOf<String, SearchResult>()

    override fun searchLocal(query: String): Flow<SearchResult> {
        localQueries += query
        return flowOf(localResults[query] ?: results[query]?.firstOrNull() ?: SearchResult.Empty)
    }

    override fun search(query: String): Flow<SearchResult> {
        queries += query
        return flowOf(*(results[query] ?: listOf(SearchResult.Empty)).toTypedArray())
    }
}

class FakeStarRatingRepository : StarRatingRepository {
    val starred = mutableMapOf<Pair<MediaKind, String>, Boolean>()
    val ratings = mutableMapOf<Pair<MediaKind, String>, Int>()
    var error: RemoteException? = null

    /** Every [setStarred] call, failed ones included. */
    var starCalls = 0

    override suspend fun setStarred(kind: MediaKind, id: String, starred: Boolean) {
        starCalls++
        error?.let { throw it }
        this.starred[kind to id] = starred
    }

    override suspend fun setRating(kind: MediaKind, id: String, rating: Int) {
        error?.let { throw it }
        ratings[kind to id] = rating
    }
}
