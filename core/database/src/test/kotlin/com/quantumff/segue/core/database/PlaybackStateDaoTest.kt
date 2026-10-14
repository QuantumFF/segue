package com.quantumff.segue.core.database

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.PendingScrobbleEntity
import com.quantumff.segue.core.database.model.QueueStateEntity
import com.quantumff.segue.core.model.DownloadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackStateDaoTest {
    private val db = inMemoryDatabase()

    @After
    fun tearDown() = db.close()

    @Test
    fun `the queue snapshot is replaced as a whole`() = runTest {
        val dao = db.queueDao()
        dao.replaceSnapshot(
            QueueStateEntity("s1", currentIndex = 1, positionMillis = 500, updatedAt = 10),
            listOf("a", "b", "a"),
        )
        dao.replaceSnapshot(QueueStateEntity("s1", currentIndex = 0, positionMillis = 0, updatedAt = 20), listOf("c"))

        assertThat(dao.songIds("s1")).containsExactly("c")
        assertThat(dao.observeState("s1").first()).isEqualTo(QueueStateEntity("s1", 0, 0, 20))
    }

    @Test
    fun `queue snapshots keep duplicates in order`() = runTest {
        db.queueDao().replaceSnapshot(QueueStateEntity("s1", 2, 0, 1), listOf("a", "b", "a"))

        assertThat(db.queueDao().songIds("s1")).containsExactly("a", "b", "a").inOrder()
    }

    @Test
    fun `pending scrobbles are read oldest first in batches and deleted once sent`() = runTest {
        val dao = db.scrobbleDao()
        dao.insert(PendingScrobbleEntity(serverId = "s1", songId = "b", playedAt = 2, submission = true))
        dao.insert(PendingScrobbleEntity(serverId = "s1", songId = "a", playedAt = 1, submission = true))
        dao.insert(PendingScrobbleEntity(serverId = "s2", songId = "c", playedAt = 0, submission = true))

        val batch = dao.oldest("s1", limit = 10)
        assertThat(batch.map { it.songId }).containsExactly("a", "b").inOrder()

        dao.delete(batch.map { it.id })

        assertThat(dao.oldest("s1", limit = 10)).isEmpty()
        assertThat(dao.oldest("s2", limit = 10)).hasSize(1)
    }

    @Test
    fun `download state is tracked per song`() = runTest {
        val dao = db.downloadDao()
        dao.upsert(DownloadEntity(serverId = "s1", songId = "a", state = DownloadState.Queued, updatedAt = 1))
        dao.upsert(
            DownloadEntity(
                serverId = "s1",
                songId = "a",
                state = DownloadState.Completed,
                filePath = "/x",
                updatedAt = 2,
            ),
        )

        assertThat(dao.observe("s1", "a").first()!!.state).isEqualTo(DownloadState.Completed)
        assertThat(dao.observeByState("s1", DownloadState.Completed).first().map { it.songId }).containsExactly("a")
    }
}
