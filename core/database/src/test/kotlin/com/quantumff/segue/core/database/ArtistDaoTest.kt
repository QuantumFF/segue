package com.quantumff.segue.core.database

import androidx.paging.PagingSource
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.ArtistEntity
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.model.DownloadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Artists tab's sorts and its Favorites and Downloaded filters (DESIGN.md §4.2). */
@RunWith(RobolectricTestRunner::class)
class ArtistDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.artistDao()

    @Before
    fun setUp() = runTest {
        dao.upsertAll(
            listOf(
                artist("a", name = "Abba").copy(albumCount = 2, starredAt = 1),
                artist("b", name = "beck").copy(albumCount = 5),
                artist("c", name = "Can").copy(albumCount = 2),
                artist("d", name = "Daft Punk").copy(albumCount = 0),
                artist("x", name = "Other server", serverId = "s2"),
            ),
        )
        db.albumDao().upsertAll(
            listOf(
                album("al1", artistId = "a").copy(createdAt = 10),
                album("al2", artistId = "a").copy(createdAt = 30),
                album("al3", artistId = "b").copy(createdAt = 20),
                album("al4", artistId = "c").copy(createdAt = 30),
                album("al5", artistId = null).copy(createdAt = 40),
            ),
        )
        db.songDao().upsertAll(
            listOf(
                // Can is downloaded as a track artist, Beck as the artist of an album with a downloaded song.
                song("s1").copy(artistId = "c"),
                song("s2", albumId = "al3"),
                // A download that has not finished does not count.
                song("s3").copy(artistId = "d"),
                // Downloads without an artist must not hide the artists that have none.
                song("s4", albumId = "al5"),
            ),
        )
        db.downloadDao().insertAll(
            listOf(
                completed("s1"),
                completed("s2"),
                completed("s4"),
                DownloadEntity(serverId = "s1", songId = "s3", state = DownloadState.Downloading, updatedAt = 0),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `name sorts by sort name, ignoring case`() = runTest {
        assertThat(dao.pagingSourceByName("s1", starred = null).loadAllIds())
            .containsExactly("a", "b", "c", "d").inOrder()
    }

    @Test
    fun `recently added puts the artist of the newest album first, artists without albums last`() = runTest {
        // Abba's newest album ties with Can's, so the name breaks the tie.
        assertThat(dao.pagingSourceByRecentlyAdded("s1", starred = null).loadAllIds())
            .containsExactly("a", "c", "b", "d").inOrder()
    }

    @Test
    fun `album count puts the artist with the most albums first`() = runTest {
        assertThat(dao.pagingSourceByAlbumCount("s1", starred = null).loadAllIds())
            .containsExactly("b", "a", "c", "d").inOrder()
    }

    @Test
    fun `favorites keeps favorite artists, or drops them when inverted`() = runTest {
        assertThat(dao.pagingSourceByName("s1", starred = true).loadAllIds()).containsExactly("a")
        assertThat(dao.pagingSourceByName("s1", starred = false).loadAllIds())
            .containsExactly("b", "c", "d").inOrder()
        assertThat(dao.observeCount("s1", starred = null).first()).isEqualTo(4)
        assertThat(dao.observeCount("s1", starred = true).first()).isEqualTo(1)
        assertThat(dao.observeCount("s1", starred = false).first()).isEqualTo(3)
    }

    @Test
    fun `downloaded keeps artists with a downloaded song, or drops them when inverted`() = runTest {
        assertThat(dao.downloadFilteredPagingSourceByName("s1", starred = null, downloaded = true).loadAllIds())
            .containsExactly("b", "c").inOrder()
        assertThat(dao.downloadFilteredPagingSourceByName("s1", starred = null, downloaded = false).loadAllIds())
            .containsExactly("a", "d").inOrder()
        assertThat(dao.observeDownloadFilteredCount("s1", starred = null, downloaded = true).first()).isEqualTo(2)
        assertThat(dao.observeDownloadFilteredCount("s1", starred = null, downloaded = false).first()).isEqualTo(2)
    }

    @Test
    fun `downloaded keeps every sort`() = runTest {
        assertThat(
            dao.downloadFilteredPagingSourceByRecentlyAdded("s1", starred = null, downloaded = true).loadAllIds(),
        ).containsExactly("c", "b").inOrder()
        assertThat(dao.downloadFilteredPagingSourceByAlbumCount("s1", starred = null, downloaded = true).loadAllIds())
            .containsExactly("b", "c").inOrder()
    }

    @Test
    fun `favorites and downloaded combine`() = runTest {
        assertThat(dao.downloadFilteredPagingSourceByName("s1", starred = false, downloaded = false).loadAllIds())
            .containsExactly("d")
        assertThat(dao.observeDownloadFilteredCount("s1", starred = true, downloaded = true).first()).isEqualTo(0)
    }

    private fun completed(songId: String) =
        DownloadEntity(serverId = "s1", songId = songId, state = DownloadState.Completed, updatedAt = 0)

    private suspend fun PagingSource<Int, ArtistEntity>.loadAllIds(): List<String> {
        val result = load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false))
        return (result as PagingSource.LoadResult.Page).data.map { it.id }
    }
}
