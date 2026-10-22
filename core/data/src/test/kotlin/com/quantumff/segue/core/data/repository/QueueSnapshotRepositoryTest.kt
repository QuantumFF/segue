package com.quantumff.segue.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.SERVER_ID
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.database.model.QueueStateEntity
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.QueueSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueSnapshotRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = FakeClock()
    private val repository = RoomQueueSnapshotRepository(FakeClientProvider(FakeSubsonicClient()), db.queueDao(), clock)

    @After
    fun tearDown() = db.close()

    @Test
    fun `a snapshot keeps the queue in order with duplicates, and where playback was`() = runTest {
        repository.save(listOf("b", "a", "b"), 1, 500)

        assertThat(repository.snapshot.first()).isEqualTo(
            QueueSnapshot(
                songIds = persistentListOf("b", "a", "b"),
                currentIndex = 1,
                positionMillis = 500,
                updatedAt = clock.nowMillis(),
            ),
        )
    }

    @Test
    fun `a snapshot keeps its queue source`() = runTest {
        repository.save(listOf("a"), 0, 0, QueueSource.Adopted("Feishin"))
        assertThat(repository.snapshot.first()?.source).isEqualTo(QueueSource.Adopted("Feishin"))

        repository.save(listOf("a"), 0, 0, QueueSource.Library(shuffled = true))
        assertThat(repository.snapshot.first()?.source).isEqualTo(QueueSource.Library(shuffled = true))
    }

    @Test
    fun `a snapshot saved without a source has an unknown source`() = runTest {
        db.queueDao().replaceSnapshot(QueueStateEntity(SERVER_ID, 0, 0, 0), listOf("a"))

        assertThat(repository.snapshot.first()?.source).isEqualTo(QueueSource.Unknown)
    }

    @Test
    fun `the seen server queue change is remembered and never moves back`() = runTest {
        assertThat(repository.seenRemoteChange()).isNull()

        repository.markRemoteChangeSeen(5_000)
        repository.markRemoteChangeSeen(4_000)

        assertThat(repository.seenRemoteChange()).isEqualTo(5_000)
    }
}
