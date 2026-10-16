package com.quantumff.segue.core.playback.sync

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.QueueSnapshot
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class QueueRestoreTest {
    private fun snapshot(ids: List<String>, current: Int, position: Long = 9_000) =
        QueueSnapshot(ids.toImmutableList(), current, position, updatedAt = 0)

    @Test
    fun `a queue restores exactly`() {
        val saved = snapshot(listOf("a", "b", "c"), current = 1)
        assertThat(
            QueueRestore.plan(saved, setOf("a", "b", "c")),
        ).isEqualTo(QueueState(listOf("a", "b", "c"), 1, 9_000))
    }

    @Test
    fun `missing songs are dropped and the current index follows its song`() {
        val saved = snapshot(listOf("a1", "x", "b1", "b2"), current = 3)

        assertThat(QueueRestore.plan(saved, available = setOf("a1", "b1", "b2")))
            .isEqualTo(QueueState(listOf("a1", "b1", "b2"), 2, 9_000))
    }

    @Test
    fun `a missing current song moves on to the next one, from the start`() {
        val saved = snapshot(listOf("a", "b", "c", "d"), current = 1)

        assertThat(QueueRestore.plan(saved, available = setOf("a", "c", "d")))
            .isEqualTo(QueueState(listOf("a", "c", "d"), 1, 0))
    }

    @Test
    fun `a missing last song falls back to the one before it`() {
        val saved = snapshot(listOf("a", "b"), current = 1)
        assertThat(QueueRestore.plan(saved, setOf("a"))).isEqualTo(QueueState(listOf("a"), 0, 0))
    }

    @Test
    fun `duplicates are kept`() {
        val saved = snapshot(listOf("a", "b", "a"), current = 2)
        assertThat(QueueRestore.plan(saved, setOf("a", "b"))).isEqualTo(QueueState(listOf("a", "b", "a"), 2, 9_000))
    }

    @Test
    fun `nothing available restores nothing`() {
        assertThat(QueueRestore.plan(snapshot(listOf("a"), 0), emptySet())).isNull()
        assertThat(QueueRestore.plan(snapshot(emptyList(), -1), emptySet())).isNull()
    }
}
