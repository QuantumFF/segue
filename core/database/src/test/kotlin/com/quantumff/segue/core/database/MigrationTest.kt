package com.quantumff.segue.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SegueDatabase::class.java)

    @Test
    fun `1 to 2 keeps the queue snapshot and adds empty queue sync state and album feed tables`() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL("INSERT INTO queue_state VALUES ('s1', 1, 500, 10)")
            db.execSQL("INSERT INTO queue_items VALUES ('s1', 0, 'a'), ('s1', 1, 'b')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true, SegueMigrations.MIGRATION_1_2).use { db ->
            db.query("SELECT currentIndex, positionMillis FROM queue_state").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(1)
                assertThat(cursor.getLong(1)).isEqualTo(500)
            }
            db.query("SELECT COUNT(*) FROM queue_items").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(2)
            }
            db.query("SELECT COUNT(*) FROM album_feed").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
            db.execSQL("INSERT INTO queue_sync_state VALUES ('s1', 123)")
            db.execSQL(
                "INSERT INTO album_feed VALUES ('s1', 'Albums', NULL, 'Name', 0, NULL, NULL, 1, 'Shuffled', 7, 3, 42)",
            )
        }
    }

    @Test
    fun `2 to 3 adds an empty album genre table and makes the next sync a full one`() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL("INSERT INTO sync_metadata VALUES ('s1', 7, 100, 200)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true, SegueMigrations.MIGRATION_2_3).use { db ->
            db.query("SELECT artistsLastModified, lastFullSyncAt, lastSyncAt FROM sync_metadata").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getLong(0)).isEqualTo(7)
                assertThat(cursor.isNull(1)).isTrue()
                assertThat(cursor.getLong(2)).isEqualTo(200)
            }
            db.query("SELECT COUNT(*) FROM album_genres").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
            db.execSQL("INSERT INTO album_genres VALUES ('s1', 'al', 'Hip-Hop')")
        }
    }

    @Test
    fun `3 to 4 makes the album feed filters nullable and keeps the running feed's filters`() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL(
                "INSERT INTO album_feed VALUES ('s1', 'Albums', NULL, 'Name', 1, 'Jazz', 1971, 0, 'Shuffled', 7, 3, 42)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 4, true, SegueMigrations.MIGRATION_3_4).use { db ->
            db.query(
                "SELECT starred, genre, year, downloaded, albumOrder, seed, cursor FROM album_feed",
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(1)
                assertThat(cursor.getString(1)).isEqualTo("Jazz")
                assertThat(cursor.getInt(2)).isEqualTo(1971)
                assertThat(cursor.isNull(3)).isTrue()
                assertThat(cursor.getString(4)).isEqualTo("Shuffled")
                assertThat(cursor.getLong(5)).isEqualTo(7)
                assertThat(cursor.getInt(6)).isEqualTo(3)
            }
        }
    }

    @Test
    fun `4 to 5 drops the album feed and keeps the queue it was growing`() {
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL("INSERT INTO queue_items VALUES ('s1', 0, 'a'), ('s1', 1, 'b')")
            db.execSQL(
                "INSERT INTO album_feed VALUES ('s1', 'Albums', NULL, 'Name', 1, NULL, NULL, NULL, 'InOrder', 7, 3, 42)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 5, true, SegueMigrations.MIGRATION_4_5).use { db ->
            db.query("SELECT COUNT(*) FROM sqlite_master WHERE name = 'album_feed'").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
            db.query("SELECT COUNT(*) FROM queue_items").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(2)
            }
        }
    }

    @Test
    fun `5 to 6 keeps the queue snapshot, with no queue source`() {
        helper.createDatabase(DB_NAME, 5).use { db ->
            db.execSQL("INSERT INTO queue_state VALUES ('s1', 1, 500, 10)")
            db.execSQL("INSERT INTO queue_items VALUES ('s1', 0, 'a'), ('s1', 1, 'b')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 6, true, SegueMigrations.MIGRATION_5_6).use { db ->
            db.query(
                "SELECT currentIndex, positionMillis, updatedAt, sourceKind, sourceId, sourceName, sourceShuffled " +
                    "FROM queue_state",
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(1)
                assertThat(cursor.getLong(1)).isEqualTo(500)
                assertThat(cursor.getLong(2)).isEqualTo(10)
                assertThat(cursor.isNull(3)).isTrue()
                assertThat(cursor.isNull(4)).isTrue()
                assertThat(cursor.isNull(5)).isTrue()
                assertThat(cursor.getInt(6)).isEqualTo(0)
            }
            db.query("SELECT COUNT(*) FROM queue_items").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(2)
            }
        }
    }

    @Test
    fun `6 to 7 keeps the library and adds the indexes the artist sorts use`() {
        helper.createDatabase(DB_NAME, 6).use { db ->
            db.execSQL(
                "INSERT INTO artists (serverId, id, name, sortName, albumCount) VALUES ('s1', 'ar', 'A', 'a', 2)",
            )
            db.execSQL(
                "INSERT INTO albums (serverId, id, name, sortName, artistId, artists, songCount, durationSeconds, " +
                    "createdAt, isCompilation, discTitles, explicitStatus) " +
                    "VALUES ('s1', 'al', 'B', 'b', 'ar', '[]', 1, 1, 5, 0, '[]', '')",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 7, true, SegueMigrations.MIGRATION_6_7).use { db ->
            db.query("SELECT albumCount FROM artists WHERE id = 'ar'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(2)
            }
            db.query("SELECT artistId, createdAt FROM albums WHERE id = 'al'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("ar")
                assertThat(cursor.getLong(1)).isEqualTo(5)
            }
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name IN ('artists', 'albums')")
                .use { cursor ->
                    val names = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                    assertThat(names).containsAtLeast(
                        "index_artists_serverId_albumCount",
                        "index_albums_serverId_artistId_createdAt",
                    )
                    // The new index starts with the same columns, so the old one would only cost writes.
                    assertThat(names).doesNotContain("index_albums_serverId_artistId")
                }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
