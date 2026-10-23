package com.quantumff.segue.core.database

import androidx.paging.PagingSource
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.model.DownloadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Songs tab's sorts and its Favorites and Downloaded filters (DESIGN.md §4.2). */
@RunWith(RobolectricTestRunner::class)
class SongDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.songDao()

    @Before
    fun setUp() = runTest {
        dao.upsertAll(
            listOf(
                // Two songs of one album, whose track order is not their title order.
                song("ns", title = "No Surprises", albumId = "ok", track = 10, artist = "Radiohead")
                    .copy(album = "OK Computer", year = 1997, createdAt = 10, playCount = 5, starredAt = 1),
                song("lu", title = "Lucky", albumId = "ok", track = 11, artist = "Radiohead")
                    .copy(album = "OK Computer", year = 1997, createdAt = 10),
                song("cr", title = "Creep", albumId = "ph", track = 2, artist = "Radiohead")
                    .copy(album = "Pablo Honey", createdAt = 30, playCount = 30),
                // Lowercase names sort among the others, not after them.
                song("br", title = "breathe", albumId = "ds", track = 2, artist = "pink floyd")
                    .copy(album = "the Dark Side", year = 1973, createdAt = 20, playCount = 50),
                song("x", title = "Other server", serverId = "s2"),
            ).map { it.withSortKeys() },
        )
        db.downloadDao().insertAll(
            listOf(
                completed("cr"),
                completed("ns"),
                // A download that has not finished does not count.
                DownloadEntity(serverId = "s1", songId = "lu", state = DownloadState.Downloading, updatedAt = 0),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `title sorts by sort name, ignoring case`() = runTest {
        assertThat(dao.pagingSourceByTitle("s1", starred = null).loadAllIds())
            .containsExactly("br", "cr", "lu", "ns").inOrder()
    }

    @Test
    fun `artist sorts by artist, then album by album in track order`() = runTest {
        assertThat(dao.pagingSourceByArtist("s1", starred = null).loadAllIds())
            .containsExactly("br", "ns", "lu", "cr").inOrder()
    }

    @Test
    fun `album sorts by album, each in track order`() = runTest {
        assertThat(dao.pagingSourceByAlbum("s1", starred = null).loadAllIds())
            .containsExactly("ns", "lu", "cr", "br").inOrder()
    }

    @Test
    fun `recently added puts the newest song first, an album's songs in track order`() = runTest {
        assertThat(dao.pagingSourceByRecentlyAdded("s1", starred = null).loadAllIds())
            .containsExactly("cr", "br", "ns", "lu").inOrder()
    }

    @Test
    fun `year puts the newest year first and songs without a year last`() = runTest {
        assertThat(dao.pagingSourceByYear("s1", starred = null).loadAllIds())
            .containsExactly("ns", "lu", "br", "cr").inOrder()
    }

    @Test
    fun `play count puts the most played song first and unplayed songs last`() = runTest {
        assertThat(dao.pagingSourceByPlayCount("s1", starred = null).loadAllIds())
            .containsExactly("br", "cr", "ns", "lu").inOrder()
    }

    @Test
    fun `favorites keeps favorite songs, or drops them when inverted`() = runTest {
        assertThat(dao.pagingSourceByTitle("s1", starred = true).loadAllIds()).containsExactly("ns")
        assertThat(dao.pagingSourceByTitle("s1", starred = false).loadAllIds())
            .containsExactly("br", "cr", "lu").inOrder()
        assertThat(dao.observeCount("s1", starred = null).first()).isEqualTo(4)
        assertThat(dao.observeCount("s1", starred = true).first()).isEqualTo(1)
        assertThat(dao.observeCount("s1", starred = false).first()).isEqualTo(3)
    }

    @Test
    fun `downloaded keeps songs with a finished download, or drops them when inverted`() = runTest {
        assertThat(dao.downloadFilteredPagingSourceByTitle("s1", starred = null, downloaded = true).loadAllIds())
            .containsExactly("cr", "ns").inOrder()
        assertThat(dao.downloadFilteredPagingSourceByTitle("s1", starred = null, downloaded = false).loadAllIds())
            .containsExactly("br", "lu").inOrder()
        assertThat(dao.observeDownloadFilteredCount("s1", starred = null, downloaded = true).first()).isEqualTo(2)
        assertThat(dao.observeDownloadFilteredCount("s1", starred = null, downloaded = false).first()).isEqualTo(2)
    }

    @Test
    fun `downloaded keeps every sort`() = runTest {
        suspend fun downloaded(source: (String, Boolean?, Boolean) -> PagingSource<Int, SongEntity>) =
            source("s1", null, true).loadAllIds()

        assertThat(downloaded(dao::downloadFilteredPagingSourceByArtist)).containsExactly("ns", "cr").inOrder()
        assertThat(downloaded(dao::downloadFilteredPagingSourceByAlbum)).containsExactly("ns", "cr").inOrder()
        assertThat(downloaded(dao::downloadFilteredPagingSourceByRecentlyAdded)).containsExactly("cr", "ns").inOrder()
        assertThat(downloaded(dao::downloadFilteredPagingSourceByYear)).containsExactly("ns", "cr").inOrder()
        assertThat(downloaded(dao::downloadFilteredPagingSourceByPlayCount)).containsExactly("cr", "ns").inOrder()
    }

    @Test
    fun `favorites and downloaded combine`() = runTest {
        assertThat(dao.downloadFilteredPagingSourceByTitle("s1", starred = false, downloaded = true).loadAllIds())
            .containsExactly("cr")
        assertThat(dao.observeDownloadFilteredCount("s1", starred = true, downloaded = false).first()).isEqualTo(0)
    }

    private fun completed(songId: String) =
        DownloadEntity(serverId = "s1", songId = songId, state = DownloadState.Completed, updatedAt = 0)

    private suspend fun PagingSource<Int, SongEntity>.loadAllIds(): List<String> {
        val result = load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false))
        return (result as PagingSource.LoadResult.Page).data.map { it.id }
    }
}
