package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OfflinePlaybackTest {
    private val songs = listOf("a", "b", "c", "d").map {
        Song(id = it, title = it, albumId = null, album = null, artist = null, artistId = null)
    }

    private fun ids(selection: PlayableSelection?) = selection?.songs?.map { it.id }

    @Test
    fun `online everything plays from the requested song`() {
        val selection = OfflinePlayback.playable(songs, 2, isOffline = false, downloaded = emptySet())

        assertThat(ids(selection)).containsExactly("a", "b", "c", "d").inOrder()
        assertThat(selection?.startIndex).isEqualTo(2)
    }

    @Test
    fun `offline skips songs that are not downloaded and keeps the requested start`() {
        val selection = OfflinePlayback.playable(songs, 2, isOffline = true, downloaded = setOf("a", "c", "d"))

        assertThat(ids(selection)).containsExactly("a", "c", "d").inOrder()
        assertThat(selection?.startIndex).isEqualTo(1)
    }

    @Test
    fun `offline start moves to the next downloaded song, wrapping to the first`() {
        val next = OfflinePlayback.playable(songs, 1, isOffline = true, downloaded = setOf("a", "d"))
        val wrapped = OfflinePlayback.playable(songs, 3, isOffline = true, downloaded = setOf("a", "b"))

        assertThat(next?.startIndex).isEqualTo(1)
        assertThat(ids(next)?.get(next!!.startIndex)).isEqualTo("d")
        assertThat(wrapped?.startIndex).isEqualTo(0)
    }

    @Test
    fun `offline with nothing downloaded plays nothing`() {
        assertThat(OfflinePlayback.playable(songs, 0, isOffline = true, downloaded = emptySet())).isNull()
        assertThat(OfflinePlayback.playable(emptyList(), 0, isOffline = false, downloaded = emptySet())).isNull()
    }

    @Test
    fun `queueing and row availability follow the downloaded set only when offline`() {
        assertThat(OfflinePlayback.queueable(songs, isOffline = true, downloaded = setOf("b")).map { it.id })
            .containsExactly("b")
        assertThat(OfflinePlayback.queueable(songs, isOffline = false, downloaded = emptySet())).hasSize(4)
        assertThat(OfflinePlayback.isAvailable("a", isOffline = true, downloaded = emptySet())).isFalse()
        assertThat(OfflinePlayback.isAvailable("a", isOffline = false, downloaded = emptySet())).isTrue()
    }
}
