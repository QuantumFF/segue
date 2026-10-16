package com.quantumff.segue.feature.queue

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.QueueItem
import org.junit.Test

class QueueListTest {
    private fun item(id: String, album: String?, position: Int) = QueueItem(
        songId = id,
        timelineIndex = position,
        title = id.uppercase(),
        artist = "Artist",
        album = album?.let { "Album $it" },
        albumId = album,
        coverArtId = null,
        durationMillis = 61_000,
        isStarred = false,
    )

    private val queue =
        listOf(item("b1", "b", 0), item("b2", "b", 1), item("a1", "a", 2), item("a2", "a", 3), item("c1", "c", 4))

    private fun List<QueueListItem>.describe() = map {
        when (it) {
            is QueueListItem.History -> "played ${it.count}"
            is QueueListItem.Track -> it.row.title + if (it.row.isCurrent) "*" else ""
        }
    }

    @Test
    fun `rows are the tracks in queue order, each naming its album`() {
        val rows = QueueList.build(queue, 0, isPlaying = true, historyExpanded = false)

        assertThat(rows.describe()).containsExactly("B1*", "B2", "A1", "A2", "C1").inOrder()
        val first = rows.first() as QueueListItem.Track
        assertThat(first.row.album).isEqualTo("Album b")
        assertThat(first.row.isPlaying).isTrue()
    }

    @Test
    fun `played tracks collapse into a history row`() {
        val rows = QueueList.build(queue, 3, isPlaying = false, historyExpanded = false)
        assertThat(rows.describe()).containsExactly("played 3", "A2*", "C1").inOrder()

        val expanded = QueueList.build(queue, 3, isPlaying = false, historyExpanded = true)
        assertThat(expanded.describe().first()).isEqualTo("played 3")
        assertThat(expanded.describe()).contains("B1")
    }

    @Test
    fun `rows carry queue positions, and keys survive duplicates`() {
        val duplicates = listOf(item("x", null, 0), item("x", null, 1), item("y", null, 2))
        val rows = QueueList.build(duplicates, 0, isPlaying = false, historyExpanded = false)
            .filterIsInstance<QueueListItem.Track>()

        assertThat(rows.map { it.position }).containsExactly(0, 1, 2).inOrder()
        assertThat(rows.map { it.key }).containsExactly("x#1", "x#2", "y#1").inOrder()
    }

    // --- Play next ---------------------------------------------------------------------------

    @Test
    fun `play next moves a track right after the current one`() {
        assertThat(QueueList.playNextMove(queueSize = 5, currentPosition = 0, from = 4)).isEqualTo(QueueMove(4, 1))
        assertThat(QueueList.playNextMove(queueSize = 5, currentPosition = 3, from = 0)).isEqualTo(QueueMove(0, 3))
        assertThat(QueueList.playNextMove(queueSize = 5, currentPosition = 0, from = 1)).isNull()
        assertThat(QueueList.playNextMove(queueSize = 5, currentPosition = 0, from = 0)).isNull()
    }

    // --- Drag --------------------------------------------------------------------------------

    @Test
    fun `dragging a track moves it, and the drop is a single move`() {
        val rows = QueueList.build(queue, 0, isPlaying = false, historyExpanded = false)
        val local = QueueList.moveLocally(rows, "c1#1", "b2#1")

        assertThat(local.describe()).containsExactly("B1*", "C1", "B2", "A1", "A2").inOrder()
        assertThat(QueueList.dropMove(rows, local, "c1#1")).isEqualTo(QueueMove(4, 1))
    }

    @Test
    fun `drop positions count the collapsed history`() {
        val rows = QueueList.build(queue, 2, isPlaying = false, historyExpanded = false)
        val local = QueueList.moveLocally(rows, "c1#1", "a1#1")

        assertThat(local.describe()).containsExactly("played 2", "C1", "A1*", "A2").inOrder()
        assertThat(QueueList.dropMove(rows, local, "c1#1")).isEqualTo(QueueMove(4, 2))
    }

    @Test
    fun `nothing moves above the history row`() {
        val rows = QueueList.build(queue, 2, isPlaying = false, historyExpanded = false)
        assertThat(QueueList.moveLocally(rows, "a2#1", QueueListItem.HISTORY_KEY)).isEqualTo(rows)
    }

    @Test
    fun `a drop back where it started is no move`() {
        val rows = QueueList.build(queue, 0, isPlaying = false, historyExpanded = false)
        assertThat(QueueList.dropMove(rows, rows, "b2#1")).isNull()
    }
}
