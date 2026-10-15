package com.quantumff.segue.core.playback.sync

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.QueueSnapshot
import com.quantumff.segue.core.model.ShuffleMode
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class QueueRestoreTest {
    private fun snapshot(
        ids: List<String>,
        current: Int,
        position: Long = 9_000,
        mode: ShuffleMode = ShuffleMode.Off,
        order: List<Int>? = null,
    ) = QueueSnapshot(ids.toImmutableList(), current, position, updatedAt = 0, mode, order?.toImmutableList())

    @Test
    fun `an album shuffle restores exactly`() {
        val saved = snapshot(
            ids = listOf("a1", "a2", "b1", "b2", "c1"),
            current = 3,
            mode = ShuffleMode.Albums,
            order = listOf(2, 3, 4, 0, 1),
        )

        val restored = QueueRestore.plan(saved, available = saved.songIds.toSet())

        assertThat(restored).isEqualTo(
            QueueState(listOf("a1", "a2", "b1", "b2", "c1"), 3, 9_000, ShuffleMode.Albums, listOf(2, 3, 4, 0, 1)),
        )
    }

    @Test
    fun `an unshuffled queue restores unshuffled`() {
        val saved = snapshot(listOf("a", "b", "c"), current = 1)
        assertThat(
            QueueRestore.plan(saved, setOf("a", "b", "c")),
        ).isEqualTo(QueueState(listOf("a", "b", "c"), 1, 9_000))
    }

    @Test
    fun `missing songs are dropped and the play order is renumbered`() {
        val saved = snapshot(
            ids = listOf("a1", "x", "b1", "b2"),
            current = 3,
            mode = ShuffleMode.Albums,
            order = listOf(2, 3, 1, 0),
        )

        val restored = QueueRestore.plan(saved, available = setOf("a1", "b1", "b2"))

        assertThat(
            restored,
        ).isEqualTo(QueueState(listOf("a1", "b1", "b2"), 2, 9_000, ShuffleMode.Albums, listOf(1, 2, 0)))
    }

    @Test
    fun `a missing current song moves on to the next one in play order, from the start`() {
        val saved = snapshot(
            ids = listOf("a", "b", "c", "d"),
            current = 1,
            mode = ShuffleMode.Tracks,
            order = listOf(3, 1, 0, 2),
        )

        val restored = QueueRestore.plan(saved, available = setOf("a", "c", "d"))!!

        // Play order a(0) c(1) d(2) → [d, a, c]; after the missing b comes a.
        assertThat(restored.playOrder).containsExactly(2, 0, 1).inOrder()
        assertThat(restored.currentIndex).isEqualTo(0)
        assertThat(restored.positionMillis).isEqualTo(0)
    }

    @Test
    fun `a missing last song falls back to the one before it`() {
        val saved = snapshot(listOf("a", "b"), current = 1)
        assertThat(QueueRestore.plan(saved, setOf("a"))).isEqualTo(QueueState(listOf("a"), 0, 0))
    }

    @Test
    fun `duplicates are kept`() {
        val saved = snapshot(listOf("a", "b", "a"), current = 2, mode = ShuffleMode.Tracks, order = listOf(2, 0, 1))
        assertThat(QueueRestore.plan(saved, setOf("a", "b"))).isEqualTo(
            QueueState(listOf("a", "b", "a"), 2, 9_000, ShuffleMode.Tracks, listOf(2, 0, 1)),
        )
    }

    @Test
    fun `nothing available restores nothing`() {
        assertThat(QueueRestore.plan(snapshot(listOf("a"), 0), emptySet())).isNull()
        assertThat(QueueRestore.plan(snapshot(emptyList(), -1), emptySet())).isNull()
    }
}
