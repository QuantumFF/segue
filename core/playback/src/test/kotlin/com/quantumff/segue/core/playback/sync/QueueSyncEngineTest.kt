package com.quantumff.segue.core.playback.sync

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.model.RemoteQueueOffer
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.testing.FakePlayQueueRemoteRepository
import com.quantumff.segue.core.playback.testing.FakeQueueSnapshotRepository
import com.quantumff.segue.core.testing.repository.FakeUserPreferencesRepository
import java.io.IOException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
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
        engine.flush(state)
        runCurrent()

        assertThat(remote.saves).containsExactly(state)
        assertThat(snapshots.saves).containsExactly(state)
        advanceTimeBy(60_000)
        assertThat(remote.saves).hasSize(1)
    }

    @Test
    fun `with queue sync off the snapshot is still saved but nothing is uploaded`() = runTest {
        preferences.preferences.update { it.copy(queueSyncEnabled = false) }
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
        engine.flush(state)
        runCurrent()
        assertThat(remote.saves).containsExactly(state)
    }

    @Test
    fun `an empty queue is neither saved nor uploaded`() = runTest {
        val engine = engine()
        engine.onQueueEvent(QueueEvent.QueueEdit, QueueState(emptyList(), -1, 0))
        engine.flush(null)
        advanceTimeBy(10_000)
        assertThat(snapshots.saves).isEmpty()
        assertThat(remote.saves).isEmpty()
    }

    @Test
    fun `clearing the queue clears the snapshot and the server queue`() = runTest {
        val engine = engine()
        engine.onQueueEvent(QueueEvent.QueueEdit, state)
        advanceTimeBy(QueueSyncEngine.DEBOUNCE_MILLIS + 1)
        engine.onQueueCleared()
        advanceTimeBy(QueueSyncEngine.DEBOUNCE_MILLIS + 1)

        val cleared = QueueState(emptyList(), -1, 0)
        assertThat(snapshots.saves).containsExactly(state, cleared).inOrder()
        assertThat(remote.saves).containsExactly(state, cleared).inOrder()
    }

    // --- Download and conflicts -------------------------------------------------------------

    @Test
    fun `a newer queue from another device is offered for the banner`() = runTest {
        snapshots.save(listOf("x"), 0, 0) // updatedAt 0 in the fake
        remote.queue = remoteQueue()
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)

        assertThat(
            engine.remoteOffer.value,
        ).isEqualTo(RemoteQueueOffer("Feishin", 2, "Title r2", positionMillis = 3_000, changedAt = 5_000))
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
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        remote.queue = remoteQueue()
        val engine = engine()
        engine.adoptions.test {
            engine.checkRemote(isPlayingLocally = false)
            assertThat(awaitItem().queue).isEqualTo(remoteQueue())
        }
        assertThat(engine.remoteOffer.value).isNull()
        assertThat(engine.takeAdoption()?.queue).isEqualTo(remoteQueue())
        assertThat(engine.takeAdoption()).isNull()
    }

    @Test
    fun `auto adopt keeps the previous local queue for undo, once`() = runTest {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        snapshots.save(listOf("x", "y"), 1, 9_000)
        remote.queue = remoteQueue()
        val engine = engine()
        engine.adoptions.test {
            engine.checkRemote(isPlayingLocally = false)
            awaitItem()
        }

        val undo = engine.takeAdoptionUndo()
        assertThat(undo?.songIds).containsExactly("x", "y").inOrder()
        assertThat(undo?.positionMillis).isEqualTo(9_000)
        assertThat(engine.takeAdoptionUndo()).isNull()
    }

    @Test
    fun `auto adopt over an empty local queue has nothing to undo`() = runTest {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        remote.queue = remoteQueue()
        val engine = engine()
        engine.adoptions.test {
            engine.checkRemote(isPlayingLocally = false)
            assertThat(awaitItem().undo).isNull()
        }
        assertThat(engine.takeAdoptionUndo()).isNull()
    }

    @Test
    fun `the adoption reports whether it can be undone`() = runTest {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        snapshots.save(listOf("x"), 0, 0)
        remote.queue = remoteQueue()
        val engine = engine()
        engine.adoptions.test {
            engine.checkRemote(isPlayingLocally = false)
            assertThat(awaitItem().undo?.songIds).containsExactly("x")
        }
    }

    @Test
    fun `playing something else drops the undo`() = runTest {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        snapshots.save(listOf("x"), 0, 0)
        remote.queue = remoteQueue()
        val engine = engine()
        engine.adoptions.test {
            engine.checkRemote(isPlayingLocally = false)
            awaitItem()
        }

        engine.onLocalPlaybackStarted()

        assertThat(engine.takeAdoptionUndo()).isNull()
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
    fun `a dismissed queue stays dismissed after a restart`() = runTest {
        snapshots.save(listOf("x"), 0, 0)
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        engine.dismissOffer()
        runCurrent()
        assertThat(snapshots.seen).isEqualTo(5_000)

        val afterRestart = engine()
        afterRestart.checkRemote(isPlayingLocally = false)
        assertThat(afterRestart.remoteOffer.value).isNull()
        assertThat(snapshots.snapshot.value!!.songIds).containsExactly("x")
    }

    @Test
    fun `accepting the offer stages the offered queue for the player, without undo`() = runTest {
        remote.queue = remoteQueue()
        val engine = engine()
        assertThat(engine.acceptOffer()).isNull()
        engine.checkRemote(isPlayingLocally = false)

        assertThat(engine.acceptOffer()).isEqualTo(RemoteAdoption(remoteQueue(), undo = null))
        assertThat(engine.remoteOffer.value).isNull()
        assertThat(engine.takeAdoption()).isEqualTo(RemoteAdoption(remoteQueue(), undo = null))
        assertThat(engine.acceptOffer()).isNull()
    }

    @Test
    fun `an adoption that never reached the player is unstaged, undo and all`() = runTest {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        snapshots.save(listOf("x"), 0, 0)
        remote.queue = remoteQueue()
        val engine = engine()
        lateinit var adoption: RemoteAdoption
        engine.adoptions.test {
            engine.checkRemote(isPlayingLocally = false)
            adoption = awaitItem()
        }

        engine.abandonAdoption(adoption)

        assertThat(engine.takeAdoption()).isNull()
        assertThat(engine.takeAdoptionUndo()).isNull()
    }

    @Test
    fun `an adoption the player took is not the controller's to abandon`() = runTest {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        snapshots.save(listOf("x"), 0, 0)
        remote.queue = remoteQueue()
        val engine = engine()
        lateinit var adoption: RemoteAdoption
        engine.adoptions.test {
            engine.checkRemote(isPlayingLocally = false)
            adoption = awaitItem()
        }
        assertThat(engine.takeAdoption()).isSameInstanceAs(adoption)

        // The player adopted it, and the answer was lost on the way back.
        engine.abandonAdoption(adoption)

        assertThat(engine.takeAdoptionUndo()?.songIds).containsExactly("x")
    }

    @Test
    fun `with queue sync off the server is not asked`() = runTest {
        preferences.preferences.update { it.copy(queueSyncEnabled = false) }
        remote.queue = remoteQueue()
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNull()
        assertThat(remote.fetches).isEqualTo(0)
    }

    // --- Play pressed outside the app ---------------------------------------------------------

    @Test
    fun `with auto adopt on, play outside the app stages a newer queue to play, with nothing to undo`() = runTest {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        snapshots.save(listOf("x"), 0, 0)
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        engine.adoptions.test {
            assertThat(engine.syncForResume(canNotify = true)).isTrue()
            expectNoEvents() // No "Continued from" snackbar: it plays at once.
        }
        runCurrent()

        assertThat(engine.takeAdoption()).isEqualTo(RemoteAdoption(remoteQueue(changedAt = 5_000), undo = null))
        assertThat(engine.takeAdoptionUndo()).isNull()
        assertThat(engine.remoteOffer.value).isNull()
        assertThat(snapshots.seen).isEqualTo(5_000)
    }

    @Test
    fun `with auto adopt off, play outside the app plays on and offers a newer queue in a notification`() = runTest {
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        assertThat(engine.syncForResume(canNotify = true)).isFalse()
        runCurrent()

        val offer = RemoteQueueOffer("Feishin", 2, "Title r2", positionMillis = 3_000, changedAt = 5_000)
        assertThat(engine.remoteOffer.value).isEqualTo(offer)
        assertThat(engine.notificationOffer.value).isEqualTo(offer)
        assertThat(engine.takeAdoption()).isNull()
    }

    @Test
    fun `the notification goes with the offer`() = runTest {
        remote.queue = remoteQueue()
        val engine = engine()
        engine.syncForResume(canNotify = true)
        runCurrent()

        engine.dismissOffer()

        assertThat(engine.notificationOffer.value).isNull()
        assertThat(engine.syncForResume(canNotify = true)).isFalse()
        runCurrent()
        assertThat(engine.notificationOffer.value).isNull() // Dismissed: not offered again.
    }

    @Test
    fun `playing on keeps the notification's offer, and play does not ask the server again`() = runTest {
        remote.queue = remoteQueue()
        val engine = engine()
        engine.syncForResume(canNotify = true)
        runCurrent()

        engine.onLocalPlaybackStarted()
        engine.syncForResume(canNotify = true)
        runCurrent()

        assertThat(engine.notificationOffer.value).isNotNull()
        assertThat(remote.fetches).isEqualTo(1)
        assertThat(engine.acceptOffer()?.queue).isEqualTo(remoteQueue())
    }

    @Test
    fun `the notification's offer outlives this device's own saves and moves to the banner`() = runTest {
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        engine.syncForResume(canNotify = true)
        runCurrent()

        // Playing on uploaded this device's queue over it.
        remote.queue = remoteQueue(by = "Segue-own", changedAt = 6_000)
        engine.checkRemote(isPlayingLocally = true)

        assertThat(engine.remoteOffer.value?.deviceName).isEqualTo("Feishin")
        assertThat(engine.notificationOffer.value).isNull()
        assertThat(engine.acceptOffer()?.queue).isEqualTo(remoteQueue(changedAt = 5_000))
    }

    @Test
    fun `continuing from the notification stages the offered queue`() = runTest {
        remote.queue = remoteQueue()
        val engine = engine()
        engine.syncForResume(canNotify = true)
        runCurrent()

        assertThat(engine.acceptOffer()).isEqualTo(RemoteAdoption(remoteQueue(), undo = null))
        assertThat(engine.notificationOffer.value).isNull()
        assertThat(engine.takeAdoption()?.queue).isEqualTo(remoteQueue())
    }

    @Test
    fun `the app checking again moves the offer from the notification to the banner`() = runTest {
        remote.queue = remoteQueue()
        val engine = engine()
        engine.syncForResume(canNotify = true)
        runCurrent()

        engine.checkRemote(isPlayingLocally = false)

        assertThat(engine.remoteOffer.value).isNotNull()
        assertThat(engine.notificationOffer.value).isNull()
    }

    @Test
    fun `without notifications, or with the notification turned off, play outside the app does not ask the server`() =
        runTest {
            remote.queue = remoteQueue()
            val engine = engine()
            assertThat(engine.syncForResume(canNotify = false)).isFalse()
            preferences.preferences.update { it.copy(offerRemoteQueueInNotification = false) }
            assertThat(engine.syncForResume(canNotify = true)).isFalse()
            runCurrent()

            assertThat(remote.fetches).isEqualTo(0)
            assertThat(engine.notificationOffer.value).isNull()
        }

    @Test
    fun `with auto adopt on, play outside the app adopts and never notifies`() = runTest {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        remote.queue = remoteQueue()
        val engine = engine()
        assertThat(engine.syncForResume(canNotify = true)).isTrue()
        runCurrent()
        assertThat(engine.notificationOffer.value).isNull()
    }

    @Test
    fun `play outside the app plays on when the server queue is ours or already seen`() = runTest {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        remote.queue = remoteQueue(by = "Segue-own", changedAt = 5_000)
        val engine = engine()
        assertThat(engine.syncForResume(canNotify = true)).isFalse()
        runCurrent()
        assertThat(snapshots.seen).isEqualTo(5_000)

        remote.queue = remoteQueue(changedAt = 4_000)
        assertThat(engine.syncForResume(canNotify = true)).isFalse()
        assertThat(engine.takeAdoption()).isNull()
    }

    @Test
    fun `play outside the app plays on when the server is slow or unreachable`() = runTest {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = true) }
        remote.queue = remoteQueue()
        remote.fetchDelayMillis = QueueSyncEngine.RESUME_CHECK_TIMEOUT_MILLIS + 1
        val engine = engine()
        assertThat(engine.syncForResume(canNotify = true)).isFalse()
        assertThat(currentTime).isEqualTo(QueueSyncEngine.RESUME_CHECK_TIMEOUT_MILLIS)

        remote.fetchDelayMillis = 0
        remote.fetchError = RemoteException.Unreachable(IOException("offline"))
        assertThat(engine.syncForResume(canNotify = true)).isFalse()
        assertThat(engine.takeAdoption()).isNull()
    }

    // --- By hand ------------------------------------------------------------------------------

    @Test
    fun `uploading by hand saves at once, even with queue sync off, and is not uploaded again`() = runTest {
        preferences.preferences.update { it.copy(queueSyncEnabled = false) }
        val engine = engine()
        engine.uploadNow(state)
        assertThat(remote.saves).containsExactly(state)

        preferences.preferences.update { it.copy(queueSyncEnabled = true) }
        engine.onQueueEvent(QueueEvent.Pause, state)
        advanceTimeBy(QueueSyncEngine.DEBOUNCE_MILLIS + 1)
        assertThat(remote.saves).containsExactly(state)
    }

    @Test
    fun `uploading by hand declines the offered queue`() = runTest {
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        engine.uploadNow(state)
        runCurrent()
        assertThat(engine.remoteOffer.value).isNull()
        assertThat(snapshots.seen).isEqualTo(5_000)
    }

    @Test
    fun `a failed upload by hand throws and is retried by the next event`() = runTest {
        remote.saveError = RemoteException.Unreachable(IOException("offline"))
        val engine = engine()
        val error = runCatching { engine.uploadNow(state) }.exceptionOrNull()
        assertThat(error).isInstanceOf(RemoteException::class.java)

        remote.saveError = null
        engine.onQueueEvent(QueueEvent.Pause, state)
        advanceTimeBy(QueueSyncEngine.DEBOUNCE_MILLIS + 1)
        assertThat(remote.saves).containsExactly(state)
    }

    @Test
    fun `downloading by hand returns any saved queue, our own and with queue sync off included`() = runTest {
        preferences.preferences.update { it.copy(queueSyncEnabled = false) }
        remote.queue = remoteQueue(by = "Segue-own")
        assertThat(engine().fetchNow()).isEqualTo(remoteQueue(by = "Segue-own"))
    }

    @Test
    fun `downloading by hand finds nothing when the saved queue is empty`() = runTest {
        remote.queue = remoteQueue().copy(songs = persistentListOf())
        assertThat(engine().fetchNow()).isNull()
    }

    @Test
    fun `a downloaded queue is not offered again`() = runTest {
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        engine.markDownloaded(checkNotNull(engine.fetchNow()))
        runCurrent()
        assertThat(engine.remoteOffer.value).isNull()

        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNull()
    }

    // --- Clock skew ---------------------------------------------------------------------------

    @Test
    fun `our own save read back from the server is remembered by its server time`() = runTest {
        remote.queue = remoteQueue(by = "Segue-own", changedAt = 8_000)
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        assertThat(snapshots.seen).isEqualTo(8_000)

        // Another client saves later by the server clock: offered whatever this device's clock says.
        snapshots.now = Long.MAX_VALUE / 2
        snapshots.save(listOf("x"), 0, 0)
        remote.queue = remoteQueue(changedAt = 9_000)
        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value?.deviceName).isEqualTo("Feishin")
    }

    @Test
    fun `a device clock far ahead does not hide another device's newer queue`() = runTest {
        snapshots.now = 10_000_000_000_000 // Local snapshot written with a clock years ahead.
        snapshots.save(listOf("x"), 0, 0)
        snapshots.seen = 4_000
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNotNull()
    }

    @Test
    fun `an old queue of another device is not offered once a later server change was seen`() = runTest {
        snapshots.now = 0 // Local clock far behind: must not matter either.
        snapshots.save(listOf("x"), 0, 0)
        snapshots.seen = 6_000
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNull()
    }

    @Test
    fun `adopting remembers the adopted save so it is not offered again`() = runTest {
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        engine.acceptOffer()
        runCurrent()
        assertThat(snapshots.seen).isEqualTo(5_000)

        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNull()
    }

    @Test
    fun `starting local playback retires the offer`() = runTest {
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNotNull()

        engine.onLocalPlaybackStarted()
        runCurrent()
        assertThat(engine.remoteOffer.value).isNull()
        assertThat(snapshots.seen).isEqualTo(5_000)
    }

    @Test
    fun `an offer is withdrawn when our own save has replaced it on the server`() = runTest {
        remote.queue = remoteQueue(changedAt = 5_000)
        val engine = engine()
        engine.checkRemote(isPlayingLocally = false)
        remote.queue = remoteQueue(by = "Segue-own", changedAt = 6_000)
        engine.checkRemote(isPlayingLocally = false)
        assertThat(engine.remoteOffer.value).isNull()
    }
}
