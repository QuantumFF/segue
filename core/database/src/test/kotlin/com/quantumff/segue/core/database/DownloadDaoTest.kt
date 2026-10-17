package com.quantumff.segue.core.database

import androidx.paging.PagingSource
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.AlbumArtistCrossRef
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.PinEntity
import com.quantumff.segue.core.database.model.PinSongRow
import com.quantumff.segue.core.database.model.PlaylistEntity
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.PinKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.downloadDao()

    @After
    fun tearDown() = db.close()

    private suspend fun pin(kind: PinKind, id: String, serverId: String = "s1") =
        dao.upsertPin(PinEntity(serverId, kind, id, name = id, createdAt = 0))

    private fun download(songId: String, state: DownloadState, updatedAt: Long = 0, serverId: String = "s1") =
        DownloadEntity(serverId = serverId, songId = songId, state = state, updatedAt = updatedAt)

    @Test
    fun `download state and pin kind names match the literals in DAO SQL`() {
        // Stored by name and matched as literals ('Completed', 'Queued', 'Song', ...) in DownloadDao,
        // BrowseDao and LibraryDaos. Renaming a value needs those queries and a migration updated.
        assertThat(DownloadState.entries.map { it.name })
            .containsAtLeast("Queued", "Downloading", "Paused", "Completed", "Failed")
        assertThat(PinKind.entries.map { it.name }).containsAtLeast("Album", "Playlist", "Artist", "Song")
    }

    @Test
    fun `pin membership covers album tracks, playlist entries, artist albums and single songs`() = runTest {
        db.albumDao().upsertAll(listOf(album("al1", artistId = "ar1"), album("al2"), album("al3")))
        db.albumDao().replaceAlbumArtists("s1", listOf("al2"), listOf(AlbumArtistCrossRef("s1", "al2", "ar1", 1)))
        db.songDao().upsertAll(
            listOf(
                song("a", albumId = "al1"),
                song("b", albumId = "al2"),
                song("c", albumId = "al3"),
                song("d", albumId = "al3"),
            ),
        )
        db.playlistDao().replacePlaylist(PlaylistEntity("s1", "p1", "Mix"), listOf("c", "a", "c"))
        pin(PinKind.Artist, "ar1")
        pin(PinKind.Playlist, "p1")
        pin(PinKind.Song, "d")

        val rows = dao.pinMembership("s1")

        assertThat(rows).containsExactly(
            PinSongRow(PinKind.Artist, "ar1", "a"),
            PinSongRow(PinKind.Artist, "ar1", "b"),
            PinSongRow(PinKind.Playlist, "p1", "c"),
            PinSongRow(PinKind.Playlist, "p1", "a"),
            PinSongRow(PinKind.Playlist, "p1", "c"),
            PinSongRow(PinKind.Song, "d", "d"),
        )
    }

    @Test
    fun `pin membership is per server`() = runTest {
        db.songDao().upsertAll(listOf(song("a", albumId = "al1"), song("a", albumId = "al1", serverId = "s2")))
        pin(PinKind.Album, "al1", serverId = "s2")

        assertThat(dao.pinMembership("s1")).isEmpty()
        assertThat(dao.pinMembership("s2")).containsExactly(PinSongRow(PinKind.Album, "al1", "a"))
    }

    @Test
    fun `the oldest queued download is claimed first and only once`() = runTest {
        dao.insertAll(
            listOf(
                download("late", DownloadState.Queued, updatedAt = 20),
                download("early", DownloadState.Queued, updatedAt = 10),
                download("paused", DownloadState.Paused, updatedAt = 1),
            ),
        )

        val first = dao.claimNext(now = 30)
        val second = dao.claimNext(now = 31)

        assertThat(first?.songId).isEqualTo("early")
        assertThat(second?.songId).isEqualTo("late")
        assertThat(dao.claimNext(now = 32)).isNull()
        assertThat(dao.get("s1", "early")?.state).isEqualTo(DownloadState.Downloading)
    }

    @Test
    fun `a paused download is not completed and interrupted ones are requeued`() = runTest {
        dao.insertAll(listOf(download("a", DownloadState.Queued), download("b", DownloadState.Queued)))
        dao.claimNext(now = 1)
        dao.claimNext(now = 1)
        dao.transition("s1", listOf("a"), listOf(DownloadState.Downloading), DownloadState.Paused, now = 2)

        assertThat(dao.complete("s1", "a", "/x", bytes = 10, now = 3)).isFalse()
        dao.requeueInterrupted()

        assertThat(dao.get("s1", "a")?.state).isEqualTo(DownloadState.Paused)
        assertThat(dao.get("s1", "b")?.state).isEqualTo(DownloadState.Queued)
    }

    @Test
    fun `the downloaded filter keeps albums and songs with a completed download`() = runTest {
        db.albumDao().upsertAll(listOf(album("al1"), album("al2")))
        db.songDao().upsertAll(listOf(song("a", albumId = "al1"), song("b", albumId = "al2")))
        dao.insertAll(listOf(download("a", DownloadState.Completed), download("b", DownloadState.Queued)))

        val albums = db.albumDao().downloadFilteredPagingSourceByName("s1", null, null, null, downloaded = true)
        val songs = db.songDao().pagingSourceDownloaded("s1")

        assertThat(albums.loadAll().map { it.id }).containsExactly("al1")
        assertThat(songs.loadAll().map { it.id }).containsExactly("a")
        assertThat(db.albumDao().observeDownloadFilteredCount("s1", null, null, null, true).first()).isEqualTo(1)
    }

    @Test
    fun `the excluded downloaded filter keeps albums without a completed download`() = runTest {
        db.albumDao().upsertAll(listOf(album("al1"), album("al2")))
        db.songDao().upsertAll(listOf(song("a", albumId = "al1"), song("b", albumId = "al2")))
        dao.insertAll(listOf(download("a", DownloadState.Completed), download("b", DownloadState.Queued)))

        val albums = db.albumDao().downloadFilteredPagingSourceByName("s1", null, null, null, downloaded = false)

        assertThat(albums.loadAll().map { it.id }).containsExactly("al2")
        assertThat(db.albumDao().observeDownloadFilteredCount("s1", null, null, null, false).first()).isEqualTo(1)
    }

    @Test
    fun `downloaded albums and artists come from completed songs only`() = runTest {
        db.albumDao().upsertAll(listOf(album("al1", artistId = "ar1"), album("al2", artistId = "ar2")))
        db.songDao().upsertAll(
            listOf(
                song("a", albumId = "al1").copy(artistId = "ar3"),
                song("b", albumId = "al2").copy(artistId = "ar2"),
            ),
        )
        dao.insertAll(listOf(download("a", DownloadState.Completed), download("b", DownloadState.Downloading)))

        assertThat(dao.observeDownloadedAlbumIds("s1").first()).containsExactly("al1")
        assertThat(dao.observeDownloadedArtistIds("s1").first()).containsExactly("ar1", "ar3")
        assertThat(dao.observeDownloadedAlbumIds("s2").first()).isEmpty()
    }

    private suspend fun <T : Any> PagingSource<Int, T>.loadAll(): List<T> {
        val result = load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false))
        return (result as PagingSource.LoadResult.Page).data
    }
}
