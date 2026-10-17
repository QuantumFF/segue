package com.quantumff.segue.core.database

import androidx.paging.PagingSource
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.AlbumArtistCrossRef
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.AlbumGenreCrossRef
import com.quantumff.segue.core.database.model.ArtistRefList
import com.quantumff.segue.core.database.model.DiscTitleList
import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.PlaylistEntity
import com.quantumff.segue.core.database.model.ReplayGainEntity
import com.quantumff.segue.core.model.ArtistRef
import com.quantumff.segue.core.model.DiscTitle
import com.quantumff.segue.core.model.DownloadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryDaoTest {
    private val db = inMemoryDatabase()

    @After
    fun tearDown() = db.close()

    @Test
    fun `album songs come in disc then track order`() = runTest {
        db.songDao().upsertAll(
            listOf(
                song("c", albumId = "al", disc = 2, track = 1),
                song("b", albumId = "al", disc = 1, track = 2),
                song("a", albumId = "al", disc = 1, track = 1),
                song("x", albumId = "other", disc = 1, track = 1),
            ),
        )

        assertThat(
            db.songDao().observeByAlbum("s1", "al").first().map {
                it.id
            },
        ).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `replacing an album's songs drops songs removed on the server`() = runTest {
        val dao = db.songDao()
        dao.upsertAll(listOf(song("a", albumId = "al"), song("b", albumId = "al"), song("z", albumId = "keep")))

        dao.replaceSongsOfAlbums("s1", albumIds = listOf("al"), songs = listOf(song("a", albumId = "al")))

        assertThat(dao.observeByAlbum("s1", "al").first().map { it.id }).containsExactly("a")
        assertThat(dao.observeByAlbum("s1", "keep").first()).hasSize(1)
    }

    @Test
    fun `OpenSubsonic fields survive a round trip`() = runTest {
        val refs = ArtistRefList(listOf(ArtistRef("1", "Radiohead"), ArtistRef("2", "Some, \"Quoted\" Name")))
        val gain = ReplayGainEntity(trackGain = -7.1f, albumGain = -6.5f, trackPeak = 0.98f, albumPeak = 1f)
        db.songDao().upsertAll(
            listOf(song("a", albumId = "al").copy(artists = refs, albumArtists = refs, replayGain = gain)),
        )
        db.albumDao().upsertAll(listOf(album("al").copy(discTitles = DiscTitleList(listOf(DiscTitle(1, "Live"))))))

        val stored = db.songDao().observe("s1", "a").first()!!
        assertThat(stored.artists).isEqualTo(refs)
        assertThat(stored.replayGain).isEqualTo(gain)
        assertThat(db.albumDao().observe("s1", "al").first()!!.discTitles.items).containsExactly(DiscTitle(1, "Live"))
    }

    @Test
    fun `a song without ReplayGain tags reads back null gain`() = runTest {
        db.songDao().upsertAll(listOf(song("a")))

        assertThat(db.songDao().observe("s1", "a").first()!!.replayGain).isNull()
    }

    @Test
    fun `an artist's albums include albums credited through the cross reference`() = runTest {
        db.albumDao().upsertAll(listOf(album("own", artistId = "ar"), album("feat", artistId = "other"), album("none")))
        db.albumDao().replaceAlbumArtists("s1", listOf("feat"), listOf(AlbumArtistCrossRef("s1", "feat", "ar", 1)))

        assertThat(db.albumDao().observeByArtist("s1", "ar").first().map { it.id }).containsExactly("own", "feat")
    }

    @Test
    fun `a genre's albums include albums that list it after their first genre`() = runTest {
        val dao = db.albumDao()
        dao.upsertAll(
            listOf(
                album("first").copy(genre = "Hip-Hop"),
                album("later").copy(genre = "Blues"),
                album("other").copy(genre = "Jazz"),
            ),
        )
        dao.replaceAlbumGenres(
            "s1",
            listOf("later"),
            listOf(AlbumGenreCrossRef("s1", "later", "Blues"), AlbumGenreCrossRef("s1", "later", "Hip-Hop")),
        )

        assertThat(dao.idsInViewOrder("s1", "Name", null, "Hip-Hop", null, downloaded = null))
            .containsExactly("first", "later").inOrder()
        assertThat(dao.observeCount("s1", starred = null, genre = "Hip-Hop", year = null).first()).isEqualTo(2)

        dao.deleteByIds("s1", listOf("later"))
        dao.upsertAll(listOf(album("later").copy(genre = "Blues")))

        assertThat(dao.idsInViewOrder("s1", "Name", null, "Hip-Hop", null, downloaded = null))
            .containsExactly("first")
    }

    @Test
    fun `deleting by id handles more ids than SQLite allows in one statement`() = runTest {
        val ids = (1..2_500).map { "al$it" }
        db.albumDao().upsertAll(ids.map { album(it) })

        db.albumDao().deleteByIds("s1", ids.drop(1))

        assertThat(db.albumDao().ids("s1")).containsExactly("al1")
    }

    @Test
    fun `known ids returns only ids already stored`() = runTest {
        db.albumDao().upsertAll(listOf(album("a"), album("b")))

        assertThat(db.albumDao().knownIds("s1", listOf("a", "c"))).containsExactly("a")
    }

    @Test
    fun `playlist songs keep order and duplicates`() = runTest {
        db.songDao().upsertAll(listOf(song("a"), song("b")))
        db.playlistDao().replacePlaylist(playlist("p"), songIds = listOf("b", "a", "b"))

        assertThat(
            db.playlistDao().observeSongs("s1", "p").first().map {
                it.id
            },
        ).containsExactly("b", "a", "b").inOrder()

        db.playlistDao().replacePlaylist(playlist("p"), songIds = listOf("a"))

        assertThat(db.playlistDao().observeSongs("s1", "p").first().map { it.id }).containsExactly("a")
    }

    @Test
    fun `album lists keep the server's order`() = runTest {
        db.albumDao().upsertAll(listOf(album("a"), album("b"), album("c")))

        db.albumListDao().replace("s1", "recent", listOf("c", "a"))

        assertThat(
            db.albumListDao().observeAlbums("s1", "recent").first().map {
                it.id
            },
        ).containsExactly("c", "a").inOrder()
    }

    @Test
    fun `clearing a server leaves other servers untouched`() = runTest {
        db.songDao().upsertAll(listOf(song("a"), song("a", serverId = "s2")))
        db.albumDao().upsertAll(listOf(album("al"), album("al", serverId = "s2")))
        db.playlistDao().replacePlaylist(playlist("p"), listOf("a"))

        db.serverDataDao().deleteServer("s1")

        assertThat(db.songDao().observe("s1", "a").first()).isNull()
        assertThat(db.songDao().observe("s2", "a").first()).isNotNull()
        assertThat(db.albumDao().ids("s2")).containsExactly("al")
        assertThat(db.playlistDao().observeAll("s1").first()).isEmpty()
    }

    @Test
    fun `album IDs in view order match the paged Library albums for every sort`() = runTest {
        val dao = db.albumDao()
        dao.upsertAll(
            listOf(
                album("a1", name = "Greatest Hits", artist = "queen").copy(year = 1981, createdAt = 5, genre = "Rock"),
                album("a2", name = "Greatest Hits", artist = "ABBA").copy(year = 1975, createdAt = 5, genre = "Pop"),
                album("a3", name = "Kid A", artist = "Radiohead").copy(year = 2000, createdAt = 9, genre = "Rock"),
                album("a4", name = "Amnesiac", artist = "radiohead").copy(year = 2001, createdAt = null),
                album("a5", name = "Blue", artist = null).copy(year = null, createdAt = 1, starredAt = 3),
                album("a0", name = "Greatest Hits", artist = "Queen").copy(year = 1981, createdAt = 5, genre = "Rock"),
            ),
        )

        suspend fun ids(sort: String, genre: String? = null, starred: Boolean? = null) =
            dao.idsInViewOrder("s1", sort, starred, genre, null, downloaded = null)

        assertThat(ids("Name")).isEqualTo(dao.pagingSourceByName("s1", null, null, null).loadAllIds())
        assertThat(ids("Artist")).isEqualTo(dao.pagingSourceByArtist("s1", null, null, null).loadAllIds())
        assertThat(ids("RecentlyAdded"))
            .isEqualTo(dao.pagingSourceByRecentlyAdded("s1", null, null, null).loadAllIds())
        assertThat(ids("Year")).isEqualTo(dao.pagingSourceByYear("s1", null, null, null).loadAllIds())
        // Duplicate names fall back to the album ID, so both lists agree on ties too.
        assertThat(ids("Name").take(3)).containsExactly("a4", "a5", "a0").inOrder()
        assertThat(ids("Year", genre = "Rock")).containsExactly("a3", "a0", "a1").inOrder()
        assertThat(ids("Name", starred = true)).containsExactly("a5")
        assertThat(ids("Name", starred = false)).containsExactly("a4", "a0", "a1", "a2", "a3").inOrder()
    }

    @Test
    fun `album IDs in view order honour the downloaded filter`() = runTest {
        db.albumDao().upsertAll(listOf(album("al1", name = "B"), album("al2", name = "A")))
        db.songDao().upsertAll(listOf(song("a", albumId = "al1"), song("b", albumId = "al2")))
        db.downloadDao().insertAll(
            listOf(DownloadEntity(serverId = "s1", songId = "a", state = DownloadState.Completed, updatedAt = 0)),
        )

        assertThat(db.albumDao().idsInViewOrder("s1", "Name", null, null, null, downloaded = true))
            .containsExactly("al1")
        assertThat(db.albumDao().idsInViewOrder("s1", "Name", null, null, null, downloaded = false))
            .containsExactly("al2")
        assertThat(db.albumDao().idsInViewOrder("s1", "Name", null, null, null, downloaded = null))
            .containsExactly("al2", "al1")
            .inOrder()
    }

    private suspend fun PagingSource<Int, AlbumEntity>.loadAllIds(): List<String> {
        val result = load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false))
        return (result as PagingSource.LoadResult.Page).data.map { it.id }
    }

    private fun playlist(id: String) = PlaylistEntity(serverId = "s1", id = id, name = id)
}
