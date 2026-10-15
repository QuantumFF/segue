package com.quantumff.segue.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.ArtistEntity
import com.quantumff.segue.core.database.model.SongEntity

/**
 * Offset/limit pages of the library for media browsers (Android Auto), which ask for one page at
 * a time and cannot use Paging 3. Each call is one bounded query.
 */
@Dao
interface BrowseDao {
    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY sortName LIMIT :limit OFFSET :offset")
    suspend fun albumsByName(serverId: String, limit: Int, offset: Int): List<AlbumEntity>

    @Query(
        "SELECT * FROM albums WHERE serverId = :serverId " +
            "ORDER BY createdAt DESC, sortName LIMIT :limit OFFSET :offset",
    )
    suspend fun albumsByRecentlyAdded(serverId: String, limit: Int, offset: Int): List<AlbumEntity>

    /** Albums by when they were last played, as of the last sync. Never-played albums are left out. */
    @Query(
        "SELECT * FROM albums WHERE serverId = :serverId AND playedAt IS NOT NULL " +
            "ORDER BY playedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun albumsByRecentlyPlayed(serverId: String, limit: Int, offset: Int): List<AlbumEntity>

    @Query("SELECT * FROM artists WHERE serverId = :serverId ORDER BY sortName LIMIT :limit OFFSET :offset")
    suspend fun artistsByName(serverId: String, limit: Int, offset: Int): List<ArtistEntity>

    /** Albums tagged with [genre], or holding at least one song in it, by name. */
    @Query(
        """
        SELECT * FROM albums WHERE serverId = :serverId AND (
            genre = :genre OR id IN (SELECT albumId FROM songs WHERE serverId = :serverId AND genre = :genre)
        )
        ORDER BY sortName LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun albumsByGenre(serverId: String, genre: String, limit: Int, offset: Int): List<AlbumEntity>

    @Query(
        "SELECT * FROM songs WHERE serverId = :serverId AND starredAt IS NOT NULL " +
            "ORDER BY starredAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun starredSongs(serverId: String, limit: Int, offset: Int): List<SongEntity>

    /** Songs whose download completed, by title. */
    @Query(
        """
        SELECT songs.* FROM downloads
        INNER JOIN songs ON songs.serverId = downloads.serverId AND songs.id = downloads.songId
        WHERE downloads.serverId = :serverId AND downloads.state = 'Completed'
        ORDER BY songs.sortName LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun downloadedSongs(serverId: String, limit: Int, offset: Int): List<SongEntity>
}
