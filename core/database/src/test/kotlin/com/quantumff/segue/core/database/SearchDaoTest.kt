package com.quantumff.segue.core.database

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.PlaylistEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchDaoTest {
    private val db = inMemoryDatabase()

    @After
    fun tearDown() = db.close()

    @Test
    fun `songs match title, artist, and album word prefixes`() = runTest {
        db.songDao().upsertAll(
            listOf(
                song("1", title = "15 Step", artist = "Radiohead").copy(album = "In Rainbows"),
                song("2", title = "Reckoner", artist = "Radiohead").copy(album = "In Rainbows"),
                song("3", title = "Rainbow Road", artist = "Someone"),
            ),
        )
        val search = db.searchDao()

        assertThat(search.songs("s1", FtsQuery.prefixMatch("rainb")!!, 10).map { it.id }).containsExactly("1", "2", "3")
        assertThat(search.songs("s1", FtsQuery.prefixMatch("radio reck")!!, 10).map { it.id }).containsExactly("2")
    }

    @Test
    fun `matching ignores diacritics`() = runTest {
        db.artistDao().upsertAll(listOf(artist("a", name = "Röyksopp")))

        assertThat(db.searchDao().artists("s1", FtsQuery.prefixMatch("royk")!!, 10).map { it.id }).containsExactly("a")
    }

    @Test
    fun `results are scoped to the server`() = runTest {
        db.albumDao().upsertAll(listOf(album("a", name = "Kid A"), album("b", name = "Kid A", serverId = "s2")))

        assertThat(db.searchDao().albums("s1", FtsQuery.prefixMatch("kid")!!, 10).map { it.id }).containsExactly("a")
    }

    @Test
    fun `the index follows updates and deletes`() = runTest {
        val dao = db.songDao()
        dao.upsertAll(listOf(song("1", title = "Old title", albumId = "al")))
        dao.upsertAll(listOf(song("1", title = "New title", albumId = "al")))

        assertThat(db.searchDao().songs("s1", FtsQuery.prefixMatch("old")!!, 10)).isEmpty()
        assertThat(db.searchDao().songs("s1", FtsQuery.prefixMatch("new")!!, 10)).hasSize(1)

        dao.replaceSongsOfAlbums("s1", albumIds = listOf("al"), songs = emptyList())

        assertThat(db.searchDao().songs("s1", FtsQuery.prefixMatch("new")!!, 10)).isEmpty()
    }

    @Test
    fun `playlists match name word prefixes on the server, by name, up to the limit`() = runTest {
        db.playlistDao().upsertAll(
            listOf(
                playlist("1", "rainy day"),
                playlist("2", "Chill"),
                playlist("3", "Rainbow Mix"),
                playlist("4", "Drain"),
                playlist("5", "Rainy Nights", serverId = "s2"),
                playlist("6", "After the Rain"),
            ),
        )
        val dao = db.playlistDao()

        assertThat(dao.search("s1", "rain", 10).map { it.id }).containsExactly("6", "3", "1").inOrder()
        assertThat(dao.search("s1", "rain", 2).map { it.id }).containsExactly("6", "3").inOrder()
        assertThat(dao.search("s1", "mix rainb", 10).map { it.id }).containsExactly("3")
        assertThat(dao.search("s1", " - ", 10)).isEmpty()
    }

    private fun playlist(id: String, name: String, serverId: String = "s1") =
        PlaylistEntity(serverId = serverId, id = id, name = name)
}
