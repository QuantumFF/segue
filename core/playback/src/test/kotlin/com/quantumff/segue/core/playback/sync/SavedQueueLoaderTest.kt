package com.quantumff.segue.core.playback.sync

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.RemotePlayQueue
import com.quantumff.segue.core.playback.media.toMediaItem
import com.quantumff.segue.core.playback.service.QueueEditor
import com.quantumff.segue.core.playback.service.SeguePlayer
import com.quantumff.segue.core.playback.testing.FakePlayQueueRemoteRepository
import com.quantumff.segue.core.playback.testing.FakeQueueSnapshotRepository
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import com.quantumff.segue.core.testing.repository.FakeUserPreferencesRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Saved queues into a real player, with queue sync listening to it as in the service: what was
 * loaded is never saved or uploaded straight back, and Undo puts the replaced queue back.
 */
@RunWith(RobolectricTestRunner::class)
class SavedQueueLoaderTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private val player = SeguePlayer(exo)
    private val remote = FakePlayQueueRemoteRepository(clientName = "Segue-own")
    private val snapshots = FakeQueueSnapshotRepository()
    private val preferences = FakeUserPreferencesRepository()
    private val library = FakeLibraryRepository().apply {
        songsState.value = listOf("x", "y", "z").map { TestData.song(it) }
    }

    /** Null online; offline the downloaded songs. */
    private var downloaded: Set<String>? = null
    private val editor = QueueEditor(player, offline = { downloaded?.let { DownloadedLibrary(songIds = it) } })

    private lateinit var queueSync: QueueSyncEngine
    private lateinit var loader: SavedQueueLoader

    private val local = QueueSnapshot(persistentListOf("x", "y", "z"), currentIndex = 1, positionMillis = 9_000, 0)
    private val localState = QueueState(listOf("x", "y", "z"), 1, 9_000)
    private val remoteState = QueueState(listOf("r1", "r2"), 1, 3_000)

    private fun remoteQueue() = RemotePlayQueue(
        songs = remoteState.songIds.map { TestData.song(it) }.toImmutableList(),
        currentIndex = remoteState.currentIndex,
        positionMillis = remoteState.positionMillis,
        changedAt = 5_000,
        changedBy = "Feishin",
    )

    @After
    fun tearDown() = exo.release()

    /** Wires queue sync to the player as the service does. */
    private fun TestScope.start() {
        queueSync = QueueSyncEngine(remote, snapshots, preferences, backgroundScope)
        exo.addListener(QueueSyncListener(player, queueSync))
        loader = SavedQueueLoader(player, queueSync, snapshots, library, editor)
    }

    /** The quiet period passes, then the app leaves the foreground: everything due is written. */
    private fun TestScope.settle() {
        advanceTimeBy(QueueSyncEngine.DEBOUNCE_MILLIS + 1)
        queueSync.flush(player.queueState())
        runCurrent()
    }

    private fun queueIds() = List(exo.mediaItemCount) { exo.getMediaItemAt(it).mediaId }

    /** This device's saved queue is in the player, playing, and another client saved a newer one. */
    private suspend fun TestScope.playingLocalQueue(autoAdopt: Boolean) {
        preferences.preferences.update { it.copy(adoptRemoteQueueAutomatically = autoAdopt) }
        snapshots.snapshot.value = local
        start()
        loader.restore()
        exo.play()
        remote.queue = remoteQueue()
    }

    @Test
    fun `at start the saved queue is restored paused, and not saved or uploaded back`() = runTest {
        snapshots.snapshot.value = local
        start()

        loader.restore()

        assertThat(player.queueState()).isEqualTo(localState)
        assertThat(exo.playWhenReady).isFalse()
        settle()
        assertThat(snapshots.saves).isEmpty()
        assertThat(remote.saves).isEmpty()
    }

    @Test
    fun `restore leaves a queue that was set meanwhile alone`() = runTest {
        snapshots.snapshot.value = local
        start()
        player.setMediaItems(listOf(TestData.song("q").toMediaItem()))

        loader.restore()

        assertThat(queueIds()).containsExactly("q")
    }

    @Test
    fun `playback resumption hands the session the saved queue, which is not saved back`() = runTest {
        snapshots.snapshot.value = local
        start()

        val resumed = checkNotNull(loader.resumption())
        player.setMediaItems(resumed.mediaItems, resumed.startIndex, resumed.startPositionMs)

        assertThat(player.queueState()).isEqualTo(localState)
        settle()
        assertThat(snapshots.saves).isEmpty()
        assertThat(remote.saves).isEmpty()
    }

    @Test
    fun `nothing saved, nothing to resume`() = runTest {
        start()

        assertThat(loader.resumption()).isNull()
        loader.restore()
        assertThat(exo.mediaItemCount).isEqualTo(0)
    }

    @Test
    fun `an automatic adoption replaces the queue, paused, and is saved here but not uploaded back`() = runTest {
        playingLocalQueue(autoAdopt = true)
        queueSync.checkRemote(isPlayingLocally = false)

        assertThat(loader.adopt()).isTrue()

        assertThat(player.queueState()).isEqualTo(remoteState)
        assertThat(exo.playWhenReady).isFalse()
        settle()
        assertThat(remote.saves).isEmpty()
        assertThat(snapshots.saves).containsExactly(remoteState)
    }

    @Test
    fun `undo puts the replaced queue back, paused, and uploads it like any edit`() = runTest {
        playingLocalQueue(autoAdopt = true)
        queueSync.checkRemote(isPlayingLocally = false)
        loader.adopt()
        settle()

        loader.undoAdoption()

        assertThat(player.queueState()).isEqualTo(localState)
        assertThat(exo.playWhenReady).isFalse()
        settle()
        assertThat(remote.saves).containsExactly(localState)

        // Only once.
        player.removeMediaItem(0)
        loader.undoAdoption()
        assertThat(queueIds()).containsExactly("y", "z").inOrder()
    }

    @Test
    fun `continue adopts the queue the banner offered, with nothing to undo`() = runTest {
        playingLocalQueue(autoAdopt = false)
        queueSync.checkRemote(isPlayingLocally = true)
        assertThat(queueSync.acceptOffer()).isTrue()
        // A later save on the server does not change what the user accepted.
        remote.queue = remoteQueue().copy(songs = persistentListOf(TestData.song("other")))

        assertThat(loader.adopt()).isTrue()

        assertThat(player.queueState()).isEqualTo(remoteState)
        assertThat(exo.playWhenReady).isFalse()
        settle()
        assertThat(remote.saves).isEmpty()
        loader.undoAdoption()
        assertThat(player.queueState()).isEqualTo(remoteState)
    }

    @Test
    fun `offline only downloaded songs of the adopted queue are queued`() = runTest {
        playingLocalQueue(autoAdopt = true)
        queueSync.checkRemote(isPlayingLocally = false)
        downloaded = setOf("r2")

        assertThat(loader.adopt()).isTrue()

        assertThat(player.queueState()).isEqualTo(QueueState(listOf("r2"), 0, 3_000))
        settle()
        assertThat(remote.saves).isEmpty()
    }

    @Test
    fun `an adoption with nothing to play leaves the queue alone and nothing to undo`() = runTest {
        playingLocalQueue(autoAdopt = true)
        queueSync.checkRemote(isPlayingLocally = false)
        downloaded = emptySet()

        assertThat(loader.adopt()).isFalse()

        assertThat(player.queueState()).isEqualTo(localState)
        player.removeMediaItem(0)
        loader.undoAdoption()
        assertThat(queueIds()).containsExactly("y", "z").inOrder()
    }

    @Test
    fun `without a staged queue adopt does nothing`() = runTest {
        playingLocalQueue(autoAdopt = false)

        assertThat(loader.adopt()).isFalse()

        assertThat(player.queueState()).isEqualTo(localState)
    }
}
