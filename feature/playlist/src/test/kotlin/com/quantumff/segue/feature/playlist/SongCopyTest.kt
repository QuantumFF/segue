package com.quantumff.segue.feature.playlist

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.testing.data.TestData
import org.junit.Test

class SongCopyTest {
    private val order = listOf("a", "b", "a", "c", "a")

    @Test
    fun `a copy is found at its position among the copies of its song`() {
        assertThat(SongCopy("a", 0).positionIn(order)).isEqualTo(0)
        assertThat(SongCopy("a", 1).positionIn(order)).isEqualTo(2)
        assertThat(SongCopy("a", 2).positionIn(order)).isEqualTo(4)
        assertThat(SongCopy("c", 0).positionIn(order)).isEqualTo(3)
    }

    @Test
    fun `a copy the order no longer has is not found`() {
        assertThat(SongCopy("a", 3).positionIn(order)).isEqualTo(-1)
        assertThat(SongCopy("z", 0).positionIn(order)).isEqualTo(-1)
    }

    @Test
    fun `removing a copy moves the song's later copies up one`() {
        val removed = SongCopy("a", 1)

        assertThat(SongCopy("a", 0).afterRemovalOf(removed)).isEqualTo(SongCopy("a", 0))
        assertThat(SongCopy("a", 2).afterRemovalOf(removed)).isEqualTo(SongCopy("a", 1))
        assertThat(SongCopy("b", 2).afterRemovalOf(removed)).isEqualTo(SongCopy("b", 2))
    }

    @Test
    fun `entries number each song among its own copies`() {
        val entries = order.map { TestData.song(it) }.toEntries()

        assertThat(entries.map { it.song.id to it.copy })
            .containsExactly("a" to 0, "b" to 0, "a" to 1, "c" to 0, "a" to 2)
            .inOrder()
        assertThat(SongCopy("a", 1).isOf(entries[2])).isTrue()
        assertThat(SongCopy("a", 1).isOf(entries[4])).isFalse()
    }
}
