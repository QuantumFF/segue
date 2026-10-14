package com.quantumff.segue.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.ServerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerDaoTest {
    private lateinit var db: SegueDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SegueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `upserted servers are read back sorted by name`() = runTest {
        val dao = db.serverDao()
        dao.upsert(ServerEntity(name = "Office", baseUrl = "https://b.example", username = "me"))
        dao.upsert(ServerEntity(name = "Home", baseUrl = "https://a.example", username = "me"))

        val names = dao.observeAll().first().map { it.name }

        assertThat(names).containsExactly("Home", "Office").inOrder()
    }
}
