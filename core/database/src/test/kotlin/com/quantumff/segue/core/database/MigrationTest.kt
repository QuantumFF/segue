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
    fun `1 to 2 keeps the queue snapshot as unshuffled and adds queue sync state`() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL("INSERT INTO queue_state VALUES ('s1', 1, 500, 10)")
            db.execSQL("INSERT INTO queue_items VALUES ('s1', 0, 'a'), ('s1', 1, 'b')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true, SegueMigrations.MIGRATION_1_2).use { db ->
            db.query("SELECT currentIndex, positionMillis, shuffleMode, playOrder FROM queue_state").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(1)
                assertThat(cursor.getLong(1)).isEqualTo(500)
                assertThat(cursor.getString(2)).isEqualTo("Off")
                assertThat(cursor.isNull(3)).isTrue()
            }
            db.query("SELECT COUNT(*) FROM queue_items").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(2)
            }
            db.execSQL("INSERT INTO queue_sync_state VALUES ('s1', 123)")
        }
    }

    @Test
    fun `2 to 3 adds an empty album feed table and keeps the queue`() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL("INSERT INTO queue_state VALUES ('s1', 0, 0, 10, 'Off', NULL)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true, SegueMigrations.MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*) FROM album_feed").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
            db.query("SELECT COUNT(*) FROM queue_state").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(1)
            }
            db.execSQL(
                "INSERT INTO album_feed VALUES ('s1', 'Albums', NULL, 'Name', 0, NULL, NULL, 1, 'Shuffled', 7, 3, 42)",
            )
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
