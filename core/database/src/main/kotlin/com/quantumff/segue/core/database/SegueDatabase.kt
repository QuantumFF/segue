package com.quantumff.segue.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.AlbumListDao
import com.quantumff.segue.core.database.dao.ArtistDao
import com.quantumff.segue.core.database.dao.BrowseDao
import com.quantumff.segue.core.database.dao.DownloadDao
import com.quantumff.segue.core.database.dao.GenreDao
import com.quantumff.segue.core.database.dao.PlaylistDao
import com.quantumff.segue.core.database.dao.QueueDao
import com.quantumff.segue.core.database.dao.ScrobbleDao
import com.quantumff.segue.core.database.dao.SearchDao
import com.quantumff.segue.core.database.dao.ServerCapabilitiesDao
import com.quantumff.segue.core.database.dao.ServerDataDao
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.database.dao.SyncMetadataDao
import com.quantumff.segue.core.database.model.AlbumArtistCrossRef
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.AlbumFtsEntity
import com.quantumff.segue.core.database.model.AlbumGenreCrossRef
import com.quantumff.segue.core.database.model.AlbumListEntryEntity
import com.quantumff.segue.core.database.model.ArtistEntity
import com.quantumff.segue.core.database.model.ArtistFtsEntity
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.GenreEntity
import com.quantumff.segue.core.database.model.PendingScrobbleEntity
import com.quantumff.segue.core.database.model.PinEntity
import com.quantumff.segue.core.database.model.PlaylistEntity
import com.quantumff.segue.core.database.model.PlaylistSongEntity
import com.quantumff.segue.core.database.model.QueueItemEntity
import com.quantumff.segue.core.database.model.QueueStateEntity
import com.quantumff.segue.core.database.model.QueueSyncStateEntity
import com.quantumff.segue.core.database.model.ServerCapabilitiesEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.database.model.SongFtsEntity
import com.quantumff.segue.core.database.model.SyncMetadataEntity
import com.quantumff.segue.core.database.util.Converters

/**
 * The library cache and local playback state. Room is the single source of truth: the UI reads
 * from here and network refreshes write here.
 *
 * Every version bump adds a migration in [SegueMigrations], with a test.
 */
@Database(
    entities = [
        ArtistEntity::class,
        ArtistFtsEntity::class,
        AlbumEntity::class,
        AlbumFtsEntity::class,
        AlbumArtistCrossRef::class,
        AlbumGenreCrossRef::class,
        SongEntity::class,
        SongFtsEntity::class,
        GenreEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        AlbumListEntryEntity::class,
        QueueStateEntity::class,
        QueueItemEntity::class,
        QueueSyncStateEntity::class,
        PendingScrobbleEntity::class,
        DownloadEntity::class,
        PinEntity::class,
        SyncMetadataEntity::class,
        ServerCapabilitiesEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SegueDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao

    abstract fun albumDao(): AlbumDao

    abstract fun songDao(): SongDao

    abstract fun genreDao(): GenreDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun albumListDao(): AlbumListDao

    abstract fun queueDao(): QueueDao

    abstract fun scrobbleDao(): ScrobbleDao

    abstract fun downloadDao(): DownloadDao

    abstract fun syncMetadataDao(): SyncMetadataDao

    abstract fun serverCapabilitiesDao(): ServerCapabilitiesDao

    abstract fun searchDao(): SearchDao

    abstract fun serverDataDao(): ServerDataDao

    abstract fun browseDao(): BrowseDao
}
