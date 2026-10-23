package com.quantumff.segue.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema migrations. Every version bump adds one here and a test in `MigrationTest`. */
object SegueMigrations {
    /**
     * 1 → 2: queue sync remembers the last server queue change it accounted for, and the album
     * feed ("Play albums" / "Shuffle albums" queued in windows) is kept next to the queue. Both
     * start empty; the queue snapshot is unchanged.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `queue_sync_state` (`serverId` TEXT NOT NULL, " +
                    "`seenRemoteChangedAt` INTEGER NOT NULL, PRIMARY KEY(`serverId`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_feed` (`serverId` TEXT NOT NULL, `sourceKind` TEXT NOT NULL, " +
                    "`sourceId` TEXT, `sort` TEXT, `starredOnly` INTEGER NOT NULL, `genre` TEXT, `year` INTEGER, " +
                    "`downloadedOnly` INTEGER NOT NULL, `albumOrder` TEXT NOT NULL, `seed` INTEGER NOT NULL, " +
                    "`cursor` INTEGER NOT NULL, `queueFingerprint` INTEGER NOT NULL, PRIMARY KEY(`serverId`))",
            )
        }
    }

    /**
     * 2 → 3: albums keep every OpenSubsonic genre in `album_genres`, so a genre lists the albums the
     * server counts in it. The table starts empty; forgetting the last full sync makes the next sync
     * refetch every album to fill it.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_genres` (`serverId` TEXT NOT NULL, `albumId` TEXT NOT NULL, " +
                    "`genre` TEXT NOT NULL, PRIMARY KEY(`serverId`, `albumId`, `genre`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_album_genres_serverId_genre` ON `album_genres` (`serverId`, `genre`)",
            )
            db.execSQL("UPDATE sync_metadata SET lastFullSyncAt = NULL")
        }
    }

    /**
     * 3 → 4: the album feed's favorites and downloaded filters can also exclude, so `starredOnly`
     * and `downloadedOnly` become nullable `starred` and `downloaded` (null: no filter, 0: exclude,
     * 1: only). A running feed keeps its filters: an old 1 stays 1 and an old 0 becomes null.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_feed_new` (`serverId` TEXT NOT NULL, `sourceKind` TEXT NOT NULL, " +
                    "`sourceId` TEXT, `sort` TEXT, `starred` INTEGER, `genre` TEXT, `year` INTEGER, " +
                    "`downloaded` INTEGER, `albumOrder` TEXT NOT NULL, `seed` INTEGER NOT NULL, " +
                    "`cursor` INTEGER NOT NULL, `queueFingerprint` INTEGER NOT NULL, PRIMARY KEY(`serverId`))",
            )
            db.execSQL(
                "INSERT INTO album_feed_new SELECT serverId, sourceKind, sourceId, sort, " +
                    "CASE WHEN starredOnly = 1 THEN 1 END, genre, year, CASE WHEN downloadedOnly = 1 THEN 1 END, " +
                    "albumOrder, seed, cursor, queueFingerprint FROM album_feed",
            )
            db.execSQL("DROP TABLE album_feed")
            db.execSQL("ALTER TABLE album_feed_new RENAME TO album_feed")
        }
    }

    /**
     * 4 → 5: album plays queue every song at once instead of running as an album feed, so the
     * running feed's table goes. A queue it was growing stays as it is.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS album_feed")
        }
    }

    /**
     * 5 → 6: the queue snapshot keeps its queue source (`QueueSourceRecord`'s fields), so it comes
     * back with the queue. A queue saved before this has none: its source is unknown.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE queue_state ADD COLUMN `sourceKind` TEXT")
            db.execSQL("ALTER TABLE queue_state ADD COLUMN `sourceId` TEXT")
            db.execSQL("ALTER TABLE queue_state ADD COLUMN `sourceName` TEXT")
            db.execSQL("ALTER TABLE queue_state ADD COLUMN `sourceShuffled` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * 6 → 7: the Artists and Songs tabs' sorts.
     *
     * Artists: album count gets an index; "Recently added" (an artist's newest album) extends the
     * albums' artist index with `createdAt`, which replaces it.
     *
     * Songs keep the server's added date (`createdAt`) and lowercased artist and album names as
     * sort keys, each sort with an index. The keys are filled here; the added date needs the
     * server, so forgetting the last full sync makes the next sync refetch every song.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_artists_serverId_albumCount` ON `artists` (`serverId`, `albumCount`)",
            )
            db.execSQL("DROP INDEX IF EXISTS `index_albums_serverId_artistId`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_albums_serverId_artistId_createdAt` " +
                    "ON `albums` (`serverId`, `artistId`, `createdAt`)",
            )

            db.execSQL("ALTER TABLE songs ADD COLUMN `createdAt` INTEGER")
            db.execSQL("ALTER TABLE songs ADD COLUMN `artistSortName` TEXT")
            db.execSQL("ALTER TABLE songs ADD COLUMN `albumSortName` TEXT")
            db.execSQL("UPDATE songs SET artistSortName = lower(artist), albumSortName = lower(album)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_songs_serverId_artistSortName_albumSortName_albumId_discNumber_track` " +
                    "ON `songs` (`serverId`, `artistSortName`, `albumSortName`, `albumId`, `discNumber`, `track`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_songs_serverId_albumSortName_albumId_discNumber_track` " +
                    "ON `songs` (`serverId`, `albumSortName`, `albumId`, `discNumber`, `track`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_songs_serverId_createdAt_albumId_discNumber_track` " +
                    "ON `songs` (`serverId` ASC, `createdAt` DESC, `albumId` ASC, `discNumber` ASC, `track` ASC)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_songs_serverId_year_albumSortName_albumId_discNumber_track` " +
                    "ON `songs` (`serverId` ASC, `year` DESC, `albumSortName` ASC, `albumId` ASC, " +
                    "`discNumber` ASC, `track` ASC)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_songs_serverId_playCount` ON `songs` (`serverId`, `playCount`)",
            )
            db.execSQL("UPDATE sync_metadata SET lastFullSyncAt = NULL")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
