package com.quantumff.segue.core.database

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.model.DownloadState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowseDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.browseDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun `albums page by name`() = runTest {
        db.albumDao().upsertAll(listOf("d", "a", "c", "b", "e").map { album(it) } + album("x", serverId = "s2"))

        assertThat(dao.albumsByName("s1", limit = 2, offset = 0).map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(dao.albumsByName("s1", limit = 2, offset = 4).map { it.id }).containsExactly("e")
    }

    @Test
    fun `recently added and recently played albums come newest first`() = runTest {
        db.albumDao().upsertAll(
            listOf(
                album("old").copy(createdAt = 1, playedAt = 30),
                album("new").copy(createdAt = 2, playedAt = 10),
                album("never").copy(createdAt = 0),
            ),
        )

        assertThat(dao.albumsByRecentlyAdded("s1", limit = 10, offset = 0).map { it.id })
            .containsExactly("new", "old", "never").inOrder()
        assertThat(dao.albumsByRecentlyPlayed("s1", limit = 10, offset = 0).map { it.id })
            .containsExactly("old", "new").inOrder()
    }

    @Test
    fun `artists page by sort name`() = runTest {
        db.artistDao().upsertAll(listOf("c", "a", "b").map { artist(it) })

        assertThat(dao.artistsByName("s1", limit = 2, offset = 1).map { it.id }).containsExactly("b", "c").inOrder()
    }

    @Test
    fun `genre albums include albums tagged with the genre or holding a song in it`() = runTest {
        db.albumDao().upsertAll(
            listOf(album("tagged").copy(genre = "Rock"), album("bysong"), album("other").copy(genre = "Jazz")),
        )
        db.songDao().upsertAll(listOf(song("s", albumId = "bysong").copy(genre = "Rock")))

        assertThat(dao.albumsByGenre("s1", "Rock", limit = 10, offset = 0).map { it.id })
            .containsExactly("bysong", "tagged").inOrder()
    }

    @Test
    fun `downloaded songs are completed downloads only, by title`() = runTest {
        db.songDao().upsertAll(listOf(song("b", title = "B"), song("a", title = "A"), song("q", title = "Q")))
        val downloads = db.downloadDao()
        downloads.upsert(DownloadEntity("s1", "b", DownloadState.Completed, updatedAt = 1))
        downloads.upsert(DownloadEntity("s1", "a", DownloadState.Completed, updatedAt = 2))
        downloads.upsert(DownloadEntity("s1", "q", DownloadState.Queued, updatedAt = 3))

        assertThat(dao.downloadedSongs("s1", limit = 10, offset = 0).map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(dao.downloadedSongs("s1", limit = 1, offset = 1).map { it.id }).containsExactly("b")
    }

    @Test
    fun `starred songs page newest first`() = runTest {
        db.songDao().upsertAll(
            listOf(song("old").copy(starredAt = 1), song("new").copy(starredAt = 2), song("none")),
        )

        assertThat(dao.starredSongs("s1", limit = 10, offset = 0).map { it.id }).containsExactly("new", "old").inOrder()
    }
}
