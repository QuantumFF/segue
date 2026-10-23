package com.quantumff.segue.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.quantumff.segue.core.database.model.AlbumArtistCrossRef
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.AlbumGenreCrossRef
import com.quantumff.segue.core.database.model.AlbumListEntryEntity
import com.quantumff.segue.core.database.model.ArtistEntity
import com.quantumff.segue.core.database.model.GenreEntity
import com.quantumff.segue.core.database.model.PlaylistEntity
import com.quantumff.segue.core.database.model.PlaylistSongEntity
import com.quantumff.segue.core.database.model.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * Android before API 31 ships SQLite with a 999 bound-parameter limit, so `IN (:ids)` queries run
 * in chunks of this size.
 */
internal const val SQL_CHUNK = 500

/** Albums tagged with `:genre` in `album_genres`. */
internal const val ALBUM_IN_GENRE =
    "id IN (SELECT albumId FROM album_genres WHERE serverId = :serverId AND genre = :genre)"

/**
 * The album filter row (DESIGN.md §4.2): favorites or the rest, one genre, one year. Null parameters
 * do not filter. A genre matches any of the album's genres, as the server counts them.
 */
private const val ALBUM_FILTER =
    "serverId = :serverId AND (:starred IS NULL OR (starredAt IS NOT NULL) = :starred) " +
        "AND (:genre IS NULL OR genre = :genre OR $ALBUM_IN_GENRE) AND (:year IS NULL OR year = :year)"

/** Albums with at least one downloaded song. Only in queries that need it, so others do not observe `downloads`. */
private const val DOWNLOADED_ALBUM =
    "id IN (SELECT s.albumId FROM songs s INNER JOIN downloads d ON d.serverId = s.serverId AND d.songId = s.id " +
        "WHERE s.serverId = :serverId AND d.state = 'Completed' AND s.albumId IS NOT NULL)"

/** [ALBUM_FILTER], keeping albums with a downloaded song if `:downloaded`, else albums without one. */
private const val ALBUM_FILTER_BY_DOWNLOAD = "$ALBUM_FILTER AND ($DOWNLOADED_ALBUM) = :downloaded"

/** The artist filter row (DESIGN.md §4.2): favorites or the rest. A null parameter does not filter. */
private const val ARTIST_FILTER = "serverId = :serverId AND (:starred IS NULL OR (starredAt IS NOT NULL) = :starred)"

/**
 * Artists with a downloaded song, as its track artist or as the artist of its album. Only in
 * queries that need it, so others do not observe `downloads`. Null IDs are left out, or
 * `NOT IN` would match nothing.
 */
private const val DOWNLOADED_ARTIST =
    "id IN (SELECT s.artistId FROM songs s INNER JOIN downloads d ON d.serverId = s.serverId AND d.songId = s.id " +
        "WHERE s.serverId = :serverId AND d.state = 'Completed' AND s.artistId IS NOT NULL " +
        "UNION SELECT a.artistId FROM albums a INNER JOIN songs s ON s.serverId = a.serverId AND s.albumId = a.id " +
        "INNER JOIN downloads d ON d.serverId = s.serverId AND d.songId = s.id " +
        "WHERE a.serverId = :serverId AND d.state = 'Completed' AND a.artistId IS NOT NULL)"

/** [ARTIST_FILTER], keeping artists with a downloaded song if `:downloaded`, else artists without one. */
private const val ARTIST_FILTER_BY_DOWNLOAD = "$ARTIST_FILTER AND ($DOWNLOADED_ARTIST) = :downloaded"

/**
 * When the artist was last added to: the newest added date among their albums, by
 * `index_albums_serverId_artistId_createdAt`. Null for an artist without albums, which sorts last.
 */
private const val ARTIST_ADDED_AT =
    "(SELECT MAX(al.createdAt) FROM albums al WHERE al.serverId = artists.serverId AND al.artistId = artists.id)"

private const val BY_ARTIST_NAME = "ORDER BY sortName, id"
private const val BY_ARTIST_ADDED = "ORDER BY $ARTIST_ADDED_AT DESC, sortName, id"
private const val BY_ALBUM_COUNT = "ORDER BY albumCount DESC, sortName, id"

@Dao
abstract class ArtistDao {
    // The Artists tab: one query per sort, then the same three limited to artists with or without a
    // downloaded song (the "Downloaded" chip, offline mode). Ties are broken by ID for stable paging.

    @Query("SELECT * FROM artists WHERE $ARTIST_FILTER $BY_ARTIST_NAME")
    abstract fun pagingSourceByName(serverId: String, starred: Boolean?): PagingSource<Int, ArtistEntity>

    @Query("SELECT * FROM artists WHERE $ARTIST_FILTER $BY_ARTIST_ADDED")
    abstract fun pagingSourceByRecentlyAdded(serverId: String, starred: Boolean?): PagingSource<Int, ArtistEntity>

    @Query("SELECT * FROM artists WHERE $ARTIST_FILTER $BY_ALBUM_COUNT")
    abstract fun pagingSourceByAlbumCount(serverId: String, starred: Boolean?): PagingSource<Int, ArtistEntity>

    @Query("SELECT COUNT(*) FROM artists WHERE $ARTIST_FILTER")
    abstract fun observeCount(serverId: String, starred: Boolean?): Flow<Int>

    @Query("SELECT * FROM artists WHERE $ARTIST_FILTER_BY_DOWNLOAD $BY_ARTIST_NAME")
    abstract fun downloadFilteredPagingSourceByName(
        serverId: String,
        starred: Boolean?,
        downloaded: Boolean,
    ): PagingSource<Int, ArtistEntity>

    @Query("SELECT * FROM artists WHERE $ARTIST_FILTER_BY_DOWNLOAD $BY_ARTIST_ADDED")
    abstract fun downloadFilteredPagingSourceByRecentlyAdded(
        serverId: String,
        starred: Boolean?,
        downloaded: Boolean,
    ): PagingSource<Int, ArtistEntity>

    @Query("SELECT * FROM artists WHERE $ARTIST_FILTER_BY_DOWNLOAD $BY_ALBUM_COUNT")
    abstract fun downloadFilteredPagingSourceByAlbumCount(
        serverId: String,
        starred: Boolean?,
        downloaded: Boolean,
    ): PagingSource<Int, ArtistEntity>

    @Query("SELECT COUNT(*) FROM artists WHERE $ARTIST_FILTER_BY_DOWNLOAD")
    abstract fun observeDownloadFilteredCount(serverId: String, starred: Boolean?, downloaded: Boolean): Flow<Int>

    @Query("SELECT * FROM artists WHERE serverId = :serverId ORDER BY sortName")
    abstract fun observeAll(serverId: String): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE serverId = :serverId AND id = :id")
    abstract fun observe(serverId: String, id: String): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE serverId = :serverId AND starredAt IS NOT NULL ORDER BY starredAt DESC")
    abstract fun observeStarred(serverId: String): Flow<List<ArtistEntity>>

    @Upsert
    abstract suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("SELECT id FROM artists WHERE serverId = :serverId")
    abstract suspend fun ids(serverId: String): List<String>

    @Query("UPDATE artists SET starredAt = :starredAt WHERE serverId = :serverId AND id = :id")
    abstract suspend fun setStarred(serverId: String, id: String, starredAt: Long?)

    @Query("UPDATE artists SET userRating = :rating WHERE serverId = :serverId AND id = :id")
    abstract suspend fun setRating(serverId: String, id: String, rating: Int?)

    @Transaction
    open suspend fun deleteByIds(serverId: String, ids: Collection<String>) {
        ids.chunked(SQL_CHUNK).forEach { deleteChunk(serverId, it) }
    }

    @Query("DELETE FROM artists WHERE serverId = :serverId AND id IN (:ids)")
    protected abstract suspend fun deleteChunk(serverId: String, ids: List<String>)
}

@Dao
abstract class AlbumDao {
    @Query("SELECT * FROM albums WHERE $ALBUM_FILTER ORDER BY sortName, id")
    abstract fun pagingSourceByName(
        serverId: String,
        starred: Boolean?,
        genre: String?,
        year: Int?,
    ): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE $ALBUM_FILTER ORDER BY artist COLLATE NOCASE, year, sortName, id")
    abstract fun pagingSourceByArtist(
        serverId: String,
        starred: Boolean?,
        genre: String?,
        year: Int?,
    ): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE $ALBUM_FILTER ORDER BY createdAt DESC, sortName, id")
    abstract fun pagingSourceByRecentlyAdded(
        serverId: String,
        starred: Boolean?,
        genre: String?,
        year: Int?,
    ): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE $ALBUM_FILTER ORDER BY year DESC, sortName, id")
    abstract fun pagingSourceByYear(
        serverId: String,
        starred: Boolean?,
        genre: String?,
        year: Int?,
    ): PagingSource<Int, AlbumEntity>

    @Query("SELECT COUNT(*) FROM albums WHERE $ALBUM_FILTER")
    abstract fun observeCount(serverId: String, starred: Boolean?, genre: String?, year: Int?): Flow<Int>

    // The same four orders limited to albums with or without a downloaded song (the "Downloaded" chip, offline mode).

    @Query("SELECT * FROM albums WHERE $ALBUM_FILTER_BY_DOWNLOAD ORDER BY sortName, id")
    abstract fun downloadFilteredPagingSourceByName(
        serverId: String,
        starred: Boolean?,
        genre: String?,
        year: Int?,
        downloaded: Boolean,
    ): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE $ALBUM_FILTER_BY_DOWNLOAD ORDER BY artist COLLATE NOCASE, year, sortName, id")
    abstract fun downloadFilteredPagingSourceByArtist(
        serverId: String,
        starred: Boolean?,
        genre: String?,
        year: Int?,
        downloaded: Boolean,
    ): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE $ALBUM_FILTER_BY_DOWNLOAD ORDER BY createdAt DESC, sortName, id")
    abstract fun downloadFilteredPagingSourceByRecentlyAdded(
        serverId: String,
        starred: Boolean?,
        genre: String?,
        year: Int?,
        downloaded: Boolean,
    ): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE $ALBUM_FILTER_BY_DOWNLOAD ORDER BY year DESC, sortName, id")
    abstract fun downloadFilteredPagingSourceByYear(
        serverId: String,
        starred: Boolean?,
        genre: String?,
        year: Int?,
        downloaded: Boolean,
    ): PagingSource<Int, AlbumEntity>

    @Query("SELECT COUNT(*) FROM albums WHERE $ALBUM_FILTER_BY_DOWNLOAD")
    abstract fun observeDownloadFilteredCount(
        serverId: String,
        starred: Boolean?,
        genre: String?,
        year: Int?,
        downloaded: Boolean,
    ): Flow<Int>

    /**
     * The IDs of the albums the paged Library list shows for this sort and filter, in the same
     * order ("Play albums"). [sort] is an `AlbumSortOrder` name; each branch mirrors the matching
     * paging query above, ties broken by ID.
     */
    @Query(
        """
        SELECT id FROM albums WHERE $ALBUM_FILTER AND (:downloaded IS NULL OR ($DOWNLOADED_ALBUM) = :downloaded)
        ORDER BY
            CASE WHEN :sort = 'Artist' THEN artist END COLLATE NOCASE,
            CASE WHEN :sort = 'Artist' THEN year END,
            CASE WHEN :sort = 'RecentlyAdded' THEN createdAt END DESC,
            CASE WHEN :sort = 'Year' THEN year END DESC,
            sortName, id
        """,
    )
    abstract suspend fun idsInViewOrder(
        serverId: String,
        sort: String,
        starred: Boolean?,
        genre: String?,
        year: Int?,
        downloaded: Boolean?,
    ): List<String>

    /** Release years present in the library, newest first. */
    @Query(
        "SELECT DISTINCT year FROM albums WHERE serverId = :serverId AND year IS NOT NULL AND year > 0 ORDER BY year DESC",
    )
    abstract fun observeYears(serverId: String): Flow<List<Int>>

    @Query("SELECT * FROM albums WHERE serverId = :serverId AND id = :id")
    abstract fun observe(serverId: String, id: String): Flow<AlbumEntity?>

    /** Albums by the artist, including albums that only credit them in OpenSubsonic `artists`. */
    @Query(
        """
        SELECT * FROM albums WHERE serverId = :serverId AND (
            artistId = :artistId OR id IN (SELECT albumId FROM album_artists WHERE serverId = :serverId AND artistId = :artistId)
        )
        ORDER BY year DESC, sortName
        """,
    )
    abstract fun observeByArtist(serverId: String, artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE serverId = :serverId AND starredAt IS NOT NULL ORDER BY starredAt DESC")
    abstract fun observeStarred(serverId: String): Flow<List<AlbumEntity>>

    @Upsert
    abstract suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("SELECT id FROM albums WHERE serverId = :serverId")
    abstract suspend fun ids(serverId: String): List<String>

    @Query("SELECT COUNT(*) FROM albums WHERE serverId = :serverId")
    abstract suspend fun count(serverId: String): Int

    /** The subset of [ids] already stored. */
    open suspend fun knownIds(serverId: String, ids: Collection<String>): List<String> =
        ids.chunked(SQL_CHUNK).flatMap { knownIdsChunk(serverId, it) }

    @Query("UPDATE albums SET starredAt = :starredAt WHERE serverId = :serverId AND id = :id")
    abstract suspend fun setStarred(serverId: String, id: String, starredAt: Long?)

    @Query("UPDATE albums SET userRating = :rating WHERE serverId = :serverId AND id = :id")
    abstract suspend fun setRating(serverId: String, id: String, rating: Int?)

    /** Replaces the artist credits of [albumIds] with [refs]. */
    @Transaction
    open suspend fun replaceAlbumArtists(
        serverId: String,
        albumIds: Collection<String>,
        refs: List<AlbumArtistCrossRef>,
    ) {
        albumIds.chunked(SQL_CHUNK).forEach { deleteAlbumArtistsChunk(serverId, it) }
        insertAlbumArtists(refs)
    }

    /** Replaces the genre tags of [albumIds] with [refs]. */
    @Transaction
    open suspend fun replaceAlbumGenres(
        serverId: String,
        albumIds: Collection<String>,
        refs: List<AlbumGenreCrossRef>,
    ) {
        albumIds.chunked(SQL_CHUNK).forEach { deleteAlbumGenresChunk(serverId, it) }
        insertAlbumGenres(refs)
    }

    /** Deletes albums with their songs, artist credits and genre tags. */
    @Transaction
    open suspend fun deleteByIds(serverId: String, ids: Collection<String>) {
        ids.chunked(SQL_CHUNK).forEach {
            deleteAlbumArtistsChunk(serverId, it)
            deleteAlbumGenresChunk(serverId, it)
            deleteSongsOfAlbumsChunk(serverId, it)
            deleteChunk(serverId, it)
        }
    }

    @Query("SELECT id FROM albums WHERE serverId = :serverId AND id IN (:ids)")
    protected abstract suspend fun knownIdsChunk(serverId: String, ids: List<String>): List<String>

    @Insert
    protected abstract suspend fun insertAlbumArtists(refs: List<AlbumArtistCrossRef>)

    @Query("DELETE FROM album_artists WHERE serverId = :serverId AND albumId IN (:albumIds)")
    protected abstract suspend fun deleteAlbumArtistsChunk(serverId: String, albumIds: List<String>)

    @Insert
    protected abstract suspend fun insertAlbumGenres(refs: List<AlbumGenreCrossRef>)

    @Query("DELETE FROM album_genres WHERE serverId = :serverId AND albumId IN (:albumIds)")
    protected abstract suspend fun deleteAlbumGenresChunk(serverId: String, albumIds: List<String>)

    @Query("DELETE FROM songs WHERE serverId = :serverId AND albumId IN (:albumIds)")
    protected abstract suspend fun deleteSongsOfAlbumsChunk(serverId: String, albumIds: List<String>)

    @Query("DELETE FROM albums WHERE serverId = :serverId AND id IN (:ids)")
    protected abstract suspend fun deleteChunk(serverId: String, ids: List<String>)
}

@Dao
abstract class SongDao {
    @Query("SELECT * FROM songs WHERE serverId = :serverId ORDER BY sortName")
    abstract fun pagingSource(serverId: String): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE serverId = :serverId AND genre = :genre ORDER BY sortName")
    abstract fun pagingSourceByGenre(serverId: String, genre: String): PagingSource<Int, SongEntity>

    @Query(
        """
        SELECT songs.* FROM songs INNER JOIN downloads ON downloads.serverId = songs.serverId AND downloads.songId = songs.id
        WHERE songs.serverId = :serverId AND downloads.state = 'Completed' ORDER BY songs.sortName
        """,
    )
    abstract fun pagingSourceDownloaded(serverId: String): PagingSource<Int, SongEntity>

    @Query(
        """
        SELECT COUNT(*) FROM songs INNER JOIN downloads ON downloads.serverId = songs.serverId AND downloads.songId = songs.id
        WHERE songs.serverId = :serverId AND downloads.state = 'Completed'
        """,
    )
    abstract fun observeDownloadedCount(serverId: String): Flow<Int>

    @Query("SELECT * FROM songs WHERE serverId = :serverId AND albumId = :albumId ORDER BY discNumber, track, sortName")
    abstract fun observeByAlbum(serverId: String, albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE serverId = :serverId AND id = :id")
    abstract fun observe(serverId: String, id: String): Flow<SongEntity?>

    @Query("SELECT COUNT(*) FROM songs WHERE serverId = :serverId")
    abstract fun observeCount(serverId: String): Flow<Int>

    @Query("SELECT * FROM songs WHERE serverId = :serverId")
    abstract suspend fun getAll(serverId: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE serverId = :serverId AND starredAt IS NOT NULL ORDER BY starredAt DESC")
    abstract fun observeStarred(serverId: String): Flow<List<SongEntity>>

    /** Songs for [ids], in no particular order. Missing IDs are skipped. */
    open suspend fun getByIds(serverId: String, ids: Collection<String>): List<SongEntity> =
        ids.distinct().chunked(SQL_CHUNK).flatMap { getByIdsChunk(serverId, it) }

    /** Songs of [albumIds], each album in disc then track order; albums in no particular order. */
    open suspend fun getByAlbumIds(serverId: String, albumIds: Collection<String>): List<SongEntity> =
        albumIds.distinct().chunked(SQL_CHUNK).flatMap { getByAlbumIdsChunk(serverId, it) }

    @Upsert
    abstract suspend fun upsertAll(songs: List<SongEntity>)

    @Query("UPDATE songs SET starredAt = :starredAt WHERE serverId = :serverId AND id = :id")
    abstract suspend fun setStarred(serverId: String, id: String, starredAt: Long?)

    @Query("UPDATE songs SET userRating = :rating WHERE serverId = :serverId AND id = :id")
    abstract suspend fun setRating(serverId: String, id: String, rating: Int?)

    /** Makes [songs] the complete track list of [albumIds]: songs no longer on those albums are removed. */
    @Transaction
    open suspend fun replaceSongsOfAlbums(serverId: String, albumIds: Collection<String>, songs: List<SongEntity>) {
        val keep = songs.mapTo(HashSet()) { it.id }
        albumIds.chunked(SQL_CHUNK).forEach { chunk ->
            val stale = idsOfAlbumsChunk(serverId, chunk).filterNot { it in keep }
            stale.chunked(SQL_CHUNK).forEach { deleteChunk(serverId, it) }
        }
        upsertAll(songs)
    }

    @Query("SELECT * FROM songs WHERE serverId = :serverId AND id IN (:ids)")
    protected abstract suspend fun getByIdsChunk(serverId: String, ids: List<String>): List<SongEntity>

    @Query(
        "SELECT * FROM songs WHERE serverId = :serverId AND albumId IN (:albumIds) " +
            "ORDER BY albumId, discNumber, track, sortName",
    )
    protected abstract suspend fun getByAlbumIdsChunk(serverId: String, albumIds: List<String>): List<SongEntity>

    @Query("SELECT id FROM songs WHERE serverId = :serverId AND albumId IN (:albumIds)")
    protected abstract suspend fun idsOfAlbumsChunk(serverId: String, albumIds: List<String>): List<String>

    @Query("DELETE FROM songs WHERE serverId = :serverId AND id IN (:ids)")
    protected abstract suspend fun deleteChunk(serverId: String, ids: List<String>)
}

@Dao
abstract class GenreDao {
    @Query("SELECT * FROM genres WHERE serverId = :serverId ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(serverId: String): Flow<List<GenreEntity>>

    @Transaction
    open suspend fun replaceAll(serverId: String, genres: List<GenreEntity>) {
        deleteAll(serverId)
        insertAll(genres)
    }

    @Query("DELETE FROM genres WHERE serverId = :serverId")
    protected abstract suspend fun deleteAll(serverId: String)

    @Upsert
    protected abstract suspend fun insertAll(genres: List<GenreEntity>)
}

@Dao
abstract class PlaylistDao {
    @Query("SELECT * FROM playlists WHERE serverId = :serverId ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(serverId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE serverId = :serverId AND id = :id")
    abstract fun observe(serverId: String, id: String): Flow<PlaylistEntity?>

    /** The playlist's songs in order, duplicates included. Songs not yet synced are skipped. */
    @Query(
        """
        SELECT songs.* FROM playlist_songs
        INNER JOIN songs ON songs.serverId = playlist_songs.serverId AND songs.id = playlist_songs.songId
        WHERE playlist_songs.serverId = :serverId AND playlist_songs.playlistId = :playlistId
        ORDER BY playlist_songs.position
        """,
    )
    abstract fun observeSongs(serverId: String, playlistId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM playlists WHERE serverId = :serverId")
    abstract suspend fun getAll(serverId: String): List<PlaylistEntity>

    @Upsert
    abstract suspend fun upsertAll(playlists: List<PlaylistEntity>)

    /** Stores the playlist and makes [songIds] its complete track list. */
    @Transaction
    open suspend fun replacePlaylist(playlist: PlaylistEntity, songIds: List<String>) {
        upsertAll(listOf(playlist))
        deleteSongs(playlist.serverId, playlist.id)
        insertSongs(songIds.mapIndexed { index, id -> PlaylistSongEntity(playlist.serverId, playlist.id, index, id) })
    }

    @Transaction
    open suspend fun deleteByIds(serverId: String, ids: Collection<String>) {
        ids.forEach {
            deleteSongs(serverId, it)
            delete(serverId, it)
        }
    }

    @Query("DELETE FROM playlists WHERE serverId = :serverId AND id = :id")
    protected abstract suspend fun delete(serverId: String, id: String)

    @Query("DELETE FROM playlist_songs WHERE serverId = :serverId AND playlistId = :playlistId")
    protected abstract suspend fun deleteSongs(serverId: String, playlistId: String)

    @Insert
    protected abstract suspend fun insertSongs(songs: List<PlaylistSongEntity>)
}

@Dao
abstract class AlbumListDao {
    @Query(
        """
        SELECT albums.* FROM album_list_entries
        INNER JOIN albums ON albums.serverId = album_list_entries.serverId AND albums.id = album_list_entries.albumId
        WHERE album_list_entries.serverId = :serverId AND album_list_entries.listType = :listType
        ORDER BY album_list_entries.position
        """,
    )
    abstract fun observeAlbums(serverId: String, listType: String): Flow<List<AlbumEntity>>

    @Transaction
    open suspend fun replace(serverId: String, listType: String, albumIds: List<String>) {
        deleteList(serverId, listType)
        insertAll(albumIds.mapIndexed { index, id -> AlbumListEntryEntity(serverId, listType, index, id) })
    }

    @Query("DELETE FROM album_list_entries WHERE serverId = :serverId AND listType = :listType")
    protected abstract suspend fun deleteList(serverId: String, listType: String)

    @Upsert
    protected abstract suspend fun insertAll(entries: List<AlbumListEntryEntity>)
}
