package com.quantumff.segue.core.database

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.database.model.LyricsEntity
import com.quantumff.segue.core.database.model.LyricsList
import com.quantumff.segue.core.model.LyricLine
import com.quantumff.segue.core.model.Lyrics
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricsDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.lyricsDao()

    @After
    fun tearDown() = db.close()

    private val synced = Lyrics(
        language = "eng",
        synced = true,
        offsetMillis = -100,
        displayArtist = "Artist",
        displayTitle = "Title",
        lines = persistentListOf(LyricLine(1_000, "timed"), LyricLine(2_500, "")),
    )
    private val plain = Lyrics(
        language = "xxx",
        synced = false,
        offsetMillis = 0,
        displayArtist = null,
        displayTitle = null,
        lines = persistentListOf(LyricLine(null, "plain")),
    )

    @Test
    fun `stored lyrics come back as they were, per server and song`() = runTest {
        dao.upsert(LyricsEntity("s1", "a", LyricsList(listOf(synced, plain))))
        dao.upsert(LyricsEntity("s2", "a", LyricsList(emptyList())))

        assertThat(dao.get("s1", "a")?.lyrics?.items).containsExactly(synced, plain).inOrder()
        assertThat(dao.get("s2", "a")?.lyrics?.items).isEmpty()
        assertThat(dao.get("s1", "b")).isNull()
    }

    @Test
    fun `deleting songs removes only their lyrics on that server`() = runTest {
        listOf("a", "b", "c").forEach { dao.upsert(LyricsEntity("s1", it, LyricsList(listOf(plain)))) }
        dao.upsert(LyricsEntity("s2", "a", LyricsList(listOf(plain))))

        dao.delete("s1", listOf("a", "b"))

        assertThat(dao.get("s1", "a")).isNull()
        assertThat(dao.get("s1", "b")).isNull()
        assertThat(dao.get("s1", "c")).isNotNull()
        assertThat(dao.get("s2", "a")).isNotNull()
    }
}
