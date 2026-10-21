package com.quantumff.segue.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * deleteServer lists its tables by hand. These tests read the schema instead, so a new table with a
 * serverId column fails here until deleteServer clears it too.
 */
@RunWith(RobolectricTestRunner::class)
class ServerDataDaoTest {
    private val db = inMemoryDatabase()
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    @After
    fun tearDown() = db.close()

    private class Column(val name: String, val type: String, val notNull: Boolean, val pk: Int)

    private fun tables(): List<String> = sql.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
        buildList { while (it.moveToNext()) add(it.getString(0)) }
    }

    private fun columns(table: String): List<Column> = sql.query("PRAGMA table_info(`$table`)").use {
        val name = it.getColumnIndexOrThrow("name")
        val type = it.getColumnIndexOrThrow("type")
        val notNull = it.getColumnIndexOrThrow("notnull")
        val pk = it.getColumnIndexOrThrow("pk")
        buildList {
            while (it.moveToNext()) {
                add(Column(it.getString(name), it.getString(type), it.getInt(notNull) == 1, it.getInt(pk)))
            }
        }
    }

    private fun serverTables(): List<String> = tables().filter { table -> columns(table).any { it.name == "serverId" } }

    /** One row for [serverId] in [table], every other column filled with a value unique to that server. */
    private fun seed(table: String, serverId: String, n: Int) {
        val columns = columns(table)
        // A lone INTEGER primary key is the rowid: leave it to SQLite.
        val rowid = columns.singleOrNull { it.pk > 0 }?.takeIf { it.type.equals("INTEGER", ignoreCase = true) }
        val filled = columns.filter { it != rowid }
        val values = filled.map { column ->
            when {
                column.name == "serverId" -> serverId
                column.type.equals("INTEGER", ignoreCase = true) -> n
                column.type.equals("REAL", ignoreCase = true) -> n.toDouble()
                column.type.equals("BLOB", ignoreCase = true) -> byteArrayOf(n.toByte())
                else -> "$table-$n"
            }
        }
        sql.execSQL(
            "INSERT INTO `$table` (${filled.joinToString { "`${it.name}`" }}) " +
                "VALUES (${filled.joinToString { "?" }})",
            values.toTypedArray(),
        )
    }

    private fun count(table: String, serverId: String): Int =
        sql.query("SELECT COUNT(*) FROM `$table` WHERE serverId = ?", arrayOf(serverId)).use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun `deleting a server clears every table with a serverId column and nothing of other servers`() = runTest {
        val tables = serverTables()
        // Guards against the schema query silently finding nothing.
        assertThat(tables).containsAtLeast("songs", "downloads", "queue_items", "server_capabilities")
        tables.forEach { table ->
            seed(table, "s1", n = 1)
            seed(table, "s2", n = 2)
        }

        db.serverDataDao().deleteServer("s1")

        tables.forEach { table ->
            assertWithMessage("rows of s1 left in $table: add it to ServerDataDao.deleteServer")
                .that(count(table, "s1")).isEqualTo(0)
            assertWithMessage("rows of s2 in $table").that(count(table, "s2")).isEqualTo(1)
        }
    }
}
