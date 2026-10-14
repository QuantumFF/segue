package com.quantumff.segue.core.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.repository.OfflineFirstServerRepository
import com.quantumff.segue.core.database.dao.ServerDao
import com.quantumff.segue.core.database.model.ServerEntity
import com.quantumff.segue.core.model.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OfflineFirstServerRepositoryTest {
    private val dao = FakeServerDao()
    private val repository = OfflineFirstServerRepository(dao)

    @Test
    fun `servers mirror the database`() = runTest {
        repository.servers.test {
            assertThat(awaitItem()).isEmpty()

            dao.upsert(ServerEntity(id = 7, name = "Home", baseUrl = "https://a.example", username = "me"))

            assertThat(awaitItem()).containsExactly(
                Server(id = "7", name = "Home", baseUrl = "https://a.example", username = "me"),
            )
        }
    }

    private class FakeServerDao : ServerDao {
        private val rows = MutableStateFlow(emptyList<ServerEntity>())

        override fun observeAll(): Flow<List<ServerEntity>> = rows

        override suspend fun upsert(server: ServerEntity): Long {
            rows.value = rows.value.filterNot { it.id == server.id } + server
            return server.id
        }
    }
}
