package com.quantumff.segue.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema migrations. Every version bump adds one here and a test in `MigrationTest`. */
object SegueMigrations {
    /**
     * 1 → 2: the queue snapshot keeps its shuffle mode and play order, and queue sync remembers
     * the last server queue change it accounted for. Existing snapshots were saved in play order
     * with shuffle off, which is what the defaults say.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `queue_state` ADD COLUMN `shuffleMode` TEXT NOT NULL DEFAULT 'Off'")
            db.execSQL("ALTER TABLE `queue_state` ADD COLUMN `playOrder` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `queue_sync_state` (`serverId` TEXT NOT NULL, " +
                    "`seenRemoteChangedAt` INTEGER NOT NULL, PRIMARY KEY(`serverId`))",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
