package com.quantumff.segue.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.testing.FakeClientProvider
import com.quantumff.segue.core.data.testing.FakeClock
import com.quantumff.segue.core.data.testing.FakeSubsonicClient
import com.quantumff.segue.core.data.testing.inMemoryDatabase
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.ShuffleMode
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
    fun `a shuffled snapshot keeps its mode and play order`() = runTest {
        repository.save(listOf("a", "b", "c"), 1, 500, ShuffleMode.Albums, listOf(1, 2, 0))

        val snapshot = repository.snapshot.first()!!
        assertThat(snapshot).isEqualTo(
            QueueSnapshot(
                songIds = persistentListOf("a", "b", "c"),
                currentIndex = 1,
                positionMillis = 500,
                updatedAt = clock.nowMillis(),
                shuffleMode = ShuffleMode.Albums,
                playOrder = persistentListOf(1, 2, 0),
            ),
        )
        assertThat(snapshot.songIdsInPlayOrder).containsExactly("b", "c", "a").inOrder()
        assertThat(snapshot.currentIndexInPlayOrder).isEqualTo(0)
    }

    @Test
    fun `an unshuffled snapshot has no play order`() = runTest {
        repository.save(listOf("a", "b"), 1, 0)

        val snapshot = repository.snapshot.first()!!
        assertThat(snapshot.shuffleMode).isEqualTo(ShuffleMode.Off)
        assertThat(snapshot.playOrder).isNull()
        assertThat(snapshot.songIdsInPlayOrder).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `the seen server queue change is remembered and never moves back`() = runTest {
        assertThat(repository.seenRemoteChange()).isNull()

        repository.markRemoteChangeSeen(5_000)
        repository.markRemoteChangeSeen(4_000)

        assertThat(repository.seenRemoteChange()).isEqualTo(5_000)
    }
}
