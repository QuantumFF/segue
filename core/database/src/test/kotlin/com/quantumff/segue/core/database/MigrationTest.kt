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

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
