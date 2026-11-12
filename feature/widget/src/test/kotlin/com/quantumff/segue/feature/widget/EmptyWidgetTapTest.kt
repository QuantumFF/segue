package com.quantumff.segue.feature.widget

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.data.repository.QueueSnapshotRepository
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.testing.data.TestData
import com.quantumff.segue.core.testing.repository.FakeLibraryRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EmptyWidgetTapTest {
    private val snapshots = FakeQueueSnapshots()
    private val library = FakeLibraryRepository()
    private val probe = SavedQueueProbe(snapshots, library)

    private val saved = QueueSnapshot(
        songIds = persistentListOf("so-1", "so-2"),
        currentIndex = 1,
        positionMillis = 42_000,
        updatedAt = 0,
    )

    @Test
    fun `the empty widget's tap resumes the saved queue when one exists`() = runTest {
        snapshots.snapshot.value = saved
        library.songsState.value = listOf(TestData.song("so-1"), TestData.song("so-2"))

        assertThat(surfaceTap(hasTrack = false, canResume = probe.canResume())).isEqualTo(SurfaceTap.ResumeSavedQueue)
    }

    @Test
    fun `with no saved queue the empty widget's tap opens the app`() = runTest {
        library.songsState.value = listOf(TestData.song("so-1"))

        assertThat(surfaceTap(hasTrack = false, canResume = probe.canResume())).isEqualTo(SurfaceTap.OpenApp)
    }

    @Test
    fun `a saved queue whose songs are all gone from the library opens the app`() = runTest {
        snapshots.snapshot.value = saved

        assertThat(probe.canResume()).isFalse()
        assertThat(surfaceTap(hasTrack = false, canResume = false)).isEqualTo(SurfaceTap.OpenApp)
    }

    @Test
    fun `with a track the tap opens Now Playing, saved queue or not`() {
        assertThat(surfaceTap(hasTrack = true, canResume = true)).isEqualTo(SurfaceTap.OpenNowPlaying)
        assertThat(surfaceTap(hasTrack = true, canResume = false)).isEqualTo(SurfaceTap.OpenNowPlaying)
    }
}

private class FakeQueueSnapshots : QueueSnapshotRepository {
    override val snapshot = MutableStateFlow<QueueSnapshot?>(null)

    override suspend fun save(songIds: List<String>, currentIndex: Int, positionMillis: Long, source: QueueSource) =
        Unit

    override suspend fun seenRemoteChange(): Long? = null

    override suspend fun markRemoteChangeSeen(changedAt: Long) = Unit
}
