package com.quantumff.segue.core.playback.sync

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.model.RemoteQueueOffer
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.testing.FakePlayQueueRemoteRepository
import com.quantumff.segue.core.playback.testing.FakeQueueSnapshotRepository
import com.quantumff.segue.core.playback.testing.FakeUserPreferencesRepository
import java.io.IOException
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueueSyncEngineTest {
    private val remote = FakePlayQueueRemoteRepository(clientName = "Segue-own")
    private val snapshots = FakeQueueSnapshotRepository()
    private val preferences = FakeUserPreferencesRepository()

    private fun TestScope.engine() = QueueSyncEngine(remote, snapshots, preferences, backgroundScope)

    private val state = QueueState(listOf("s3", "s1", "s2"), currentIndex = 1, positionMillis = 42_000)

    private fun remoteQueue(by: String = "Feishin", changedAt: Long = 5_000) = RemotePlayQueue(
        songs = listOf("r1", "r2").map {
            Song(id = it, title = "Title $it", albumId = null, album = null, artist = null, artistId = null)
        }.toImmutableList(),
        currentIndex = 1,
        positionMillis = 3_000,
        changedAt = changedAt,
        changedBy = by,
    )

    // --- Upload and snapshot ----------------------------------------------------------------

    @Test
    fun `queue events are debounced into one snapshot and one upload of the effective order`() = runTest {
        val engine = engine()
        engine.onQueueEvent(QueueEvent.TrackTransition, state.copy(positionMillis = 0))
        engine.onQueueEvent(QueueEvent.Seek, state.copy(positionMillis = 30_000))
        engine.onQueueEvent(QueueEvent.Seek, state)
        advanceTimeBy(QueueSyncEngine.DEBOUNCE_MILLIS + 1)

        assertThat(remote.saves).containsExactly(state)
        assertThat(snapshots.saves).containsExactly(state)
    }

    @Test
    fun `going to the background flushes at once`() = runTest {
        val engine = engine()
        engine.onQueueEvent(QueueEvent.Pause, state.copy(positionMillis = 1))
        engine.onAppBackground(state)
        runCurrent()

        assertThat(remote.saves).containsExactly(state)
        assertThat(snapshots.saves).containsExactly(state)
        advanceTimeBy(60_000)
        assertThat(remote.saves).hasSize(1)
    }

    @Test
    fun `with queue sync off the snapshot is still saved but nothing is uploaded`() = runTest {
        preferences.update { it.copy(queueSyncEnabled = false) }
        val engine = engine()
        engine.onQueueEvent(QueueEvent.QueueEdit, state)
        advanceTimeBy(QueueSyncEngine.DEBOUNCE_MILLIS + 1)

        assertThat(snapshots.saves).containsExactly(state)
        assertThat(remote.saves).isEmpty()
    }

    @Test
    fun `offline the snapshot is saved and the upload is retried on the next flush`() = runTest {
        remote.saveError = RemoteException.Unreachable(IOException("offline"))
        val engine = engine()
        engine.onQueueEvent(QueueEvent.Pause, state)
        advanceTimeBy(QueueSyncEngine.DEBOUNCE_MILLIS + 1)
        assertThat(snapshots.saves).containsExactly(state)
        assertThat(remote.saves).isEmpty()

        remote.saveError = null
        engine.onAppBackground(state)
        runCurrent()
        assertThat(remote.saves).containsExactly(state)
    }

    @Test
    fun `an empty queue is neither saved nor uploaded`() = runTest {
        val engine = engine()
        engine.onQueueEvent(QueueEvent.QueueEdit, QueueState(emptyList(), -1, 0))
        engine.onAppBackground(null)
        advanceTimeBy(10_000)
        assertThat(snapshots.saves).isEmpty()
        assertThat(remote.saves).isEmpty()
    }

    @Test
    fun `a queue restored from the snapshot is not written back or uploaded`() = runTest {
        val engine = engine()
        engine.markRestored(state)
        engine.onQueueEvent(QueueEvent.QueueEdit, state)
        engine.onAppBackground(state)
        advanceTimeBy(10_000)
        assertThat(snapshots.saves).isEmpty()
        assertThat(remote.saves).isEmpty()
    }

    // --- Download and conflicts -------------------------------------------------------------

    @Test
    fun `a newer queue from another device is offered for the banner`() = runTest {
        snapshots.save(listOf("x"), 0, 0) // updatedAt 0 in the fake
        remote.queue = remoteQueue()
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)

        assertThat(engine.remoteOffer.value).isEqualTo(RemoteQueueOffer("Feishin", 2, "Title r2"))
    }

    @Test
    fun `our own echo is not offered`() = runTest {
        remote.queue = remoteQueue(by = "Segue-own")
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNull()
    }

    @Test
    fun `offline the check leaves things as they are`() = runTest {
        remote.fetchError = RemoteException.Unreachable(IOException("offline"))
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNull()
    }

    @Test
    fun `with auto adopt on the queue is handed over for adoption instead of offered`() = runTest {
        preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        remote.queue = remoteQueue()
        val engine = engine()
        engine.adoptions.test {
            engine.checkRemote(isPlayingLocally = false)
            assertThat(awaitItem()).isEqualTo(remoteQueue())
        }
        assertThat(engine.remoteOffer.value).isNull()
    }

    @Test
    fun `a dismissed queue is not offered again`() = runTest {
        remote.queue = remoteQueue()
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        engine.dismissOffer()
        assertThat(engine.remoteOffer.value).isNull()

        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNull()
    }

    @Test
    fun `accepting the offer returns the queue and the adopted state is not uploaded back`() = runTest {
        remote.queue = remoteQueue()
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)

        val adopted = engine.acceptOffer()
        assertThat(adopted).isEqualTo(remoteQueue())
        assertThat(engine.remoteOffer.value).isNull()

        val adoptedState = QueueState(listOf("r1", "r2"), 1, 3_000)
        engine.markAdopted(adoptedState)
        engine.onQueueEvent(QueueEvent.QueueEdit, adoptedState)
        advanceTimeBy(QueueSyncEngine.DEBOUNCE_MILLIS + 1)
        assertThat(remote.saves).isEmpty()
        assertThat(snapshots.saves).containsExactly(adoptedState)
    }

    @Test
    fun `with queue sync off the server is not asked`() = runTest {
        preferences.update { it.copy(queueSyncEnabled = false) }
        remote.queue = remoteQueue()
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNull()
        assertThat(remote.fetches).isEqualTo(0)
    }
}
