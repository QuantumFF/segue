package com.quantumff.segue.feature.queue

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.QueueItem
import com.quantumff.segue.core.model.ShuffleMode
import org.junit.Test

class QueueListTest {
    private fun item(id: String, album: String?, timelineIndex: Int) = QueueItem(
        songId = id,
        timelineIndex = timelineIndex,
        title = id.uppercase(),
        artist = "Artist",
        album = album?.let { "Album $it" },
        albumId = album,
        coverArtId = null,
        durationMillis = 61_000,
        isStarred = false,
    )

    /** Play order b1 b2 a1 a2 c1 (an album shuffle of the queue a1 a2 b1 b2 c1). */
    private val albumShuffled = listOf(
        item("b1", "b", 2),
        item("b2", "b", 3),
        item("a1", "a", 0),
        item("a2", "a", 1),
        item("c1", "c", 4),
    )

    private fun List<QueueListItem>.describe() = map {
        when (it) {
            is QueueListItem.History -> "played ${it.count}"
            is QueueListItem.AlbumHeader -> "[${it.title}]"
            is QueueListItem.Track -> it.row.title + if (it.row.isCurrent) "*" else ""
        }
    }

    @Test
    fun `album shuffle rows start each album block with a header, in play order`() {
        val rows = QueueList.build(albumShuffled, 0, ShuffleMode.Albums, isPlaying = true, historyExpanded = false)

        assertThat(rows.describe())
            .containsExactly("[Album b]", "B1*", "B2", "[Album a]", "A1", "A2", "[Album c]", "C1")
            .inOrder()
        assertThat((rows[0] as QueueListItem.AlbumHeader).blockKey).isEqualTo((rows[1] as QueueListItem.Track).blockKey)
        // The header names the album, so the rows do not repeat it.
        assertThat((rows[1] as QueueListItem.Track).row.album).isNull()
        assertThat((rows[1] as QueueListItem.Track).row.isPlaying).isTrue()
    }

    @Test
    fun `without album shuffle there are no headers and rows keep the album`() {
        val rows = QueueList.build(albumShuffled, 0, ShuffleMode.Tracks, isPlaying = false, historyExpanded = false)
        assertThat(rows.none { it is QueueListItem.AlbumHeader }).isTrue()
        assertThat((rows[0] as QueueListItem.Track).row.album).isEqualTo("Album b")
    }

    @Test
    fun `played tracks collapse into a history row, and a block cut by it still gets its header`() {
        val rows = QueueList.build(albumShuffled, 3, ShuffleMode.Albums, isPlaying = false, historyExpanded = false)
        assertThat(rows.describe()).containsExactly("played 3", "[Album a]", "A2*", "[Album c]", "C1").inOrder()

        val expanded = QueueList.build(albumShuffled, 3, ShuffleMode.Albums, isPlaying = false, historyExpanded = true)
        assertThat(expanded.describe().first()).isEqualTo("played 3")
        assertThat(expanded.describe()).contains("B1")
    }

    @Test
    fun `rows carry play positions and queue indices, and keys survive duplicates`() {
        val queue = listOf(item("x", null, 1), item("x", null, 0), item("y", null, 2))
        val rows = QueueList.build(queue, 0, ShuffleMode.Tracks, isPlaying = false, historyExpanded = false)
            .filterIsInstance<QueueListItem.Track>()

        assertThat(rows.map { it.playPosition }).containsExactly(0, 1, 2).inOrder()
        assertThat(rows.map { it.timelineIndex }).containsExactly(1, 0, 2).inOrder()
        assertThat(rows.map { it.key }).containsExactly("x#2", "x#1", "y#1").inOrder()
    }

    @Test
    fun `an album that comes back later in the queue is a second block`() {
        val queue = listOf(item("a1", "a", 0), item("b1", "b", 1), item("a2", "a", 2))
        val rows = QueueList.build(queue, 0, ShuffleMode.Albums, isPlaying = false, historyExpanded = false)
        val headers = rows.filterIsInstance<QueueListItem.AlbumHeader>()
        assertThat(headers.map { it.key }).containsExactly("header:a#1", "header:b#1", "header:a#2").inOrder()
    }

    // --- Play next ---------------------------------------------------------------------------

    @Test
    fun `play next moves a track right after the current one`() {
        assertThat(QueueList.playNextMove(albumShuffled, 0, 4, ShuffleMode.Tracks)).isEqualTo(QueueMove(4, 1))
        assertThat(QueueList.playNextMove(albumShuffled, 3, 0, ShuffleMode.Tracks)).isEqualTo(QueueMove(0, 3))
        assertThat(QueueList.playNextMove(albumShuffled, 0, 1, ShuffleMode.Tracks)).isNull()
        assertThat(QueueList.playNextMove(albumShuffled, 0, 0, ShuffleMode.Tracks)).isNull()
    }

    @Test
    fun `in album shuffle play next of another album goes after the current album`() {
        assertThat(QueueList.playNextMove(albumShuffled, 0, 4, ShuffleMode.Albums)).isEqualTo(QueueMove(4, 2))
        // From before the current album: it lands after that album's last track.
        assertThat(QueueList.playNextMove(albumShuffled, 2, 1, ShuffleMode.Albums)).isEqualTo(QueueMove(1, 3))
        // A track of the current album still plays right after the current track.
        assertThat(QueueList.playNextMove(albumShuffled, 3, 2, ShuffleMode.Albums)).isEqualTo(QueueMove(2, 3))
    }

    // --- Drag --------------------------------------------------------------------------------

    @Test
    fun `dragging a track moves it, and the drop is a single move in play order`() {
        val rows = QueueList.build(albumShuffled, 0, ShuffleMode.Tracks, isPlaying = false, historyExpanded = false)
        val local = QueueList.moveLocally(rows, "c1#1", "b2#1")

        assertThat(local.describe()).containsExactly("B1*", "C1", "B2", "A1", "A2").inOrder()
        assertThat(QueueList.dropMove(rows, local, "c1#1")).isEqualTo(QueueMove(4, 1))
    }

    @Test
    fun `drop positions count the collapsed history`() {
        val rows = QueueList.build(albumShuffled, 2, ShuffleMode.Tracks, isPlaying = false, historyExpanded = false)
        val local = QueueList.moveLocally(rows, "c1#1", "a1#1")

        assertThat(local.describe()).containsExactly("played 2", "C1", "A1*", "A2").inOrder()
        assertThat(QueueList.dropMove(rows, local, "c1#1")).isEqualTo(QueueMove(4, 2))
    }

    @Test
    fun `nothing moves above the history row`() {
        val rows = QueueList.build(albumShuffled, 2, ShuffleMode.Tracks, isPlaying = false, historyExpanded = false)
        assertThat(QueueList.moveLocally(rows, "a2#1", QueueListItem.HISTORY_KEY)).isEqualTo(rows)
    }

    @Test
    fun `dragging an album header moves the whole block past whole blocks`() {
        val rows = QueueList.build(albumShuffled, 0, ShuffleMode.Albums, isPlaying = false, historyExpanded = false)

        // Album a's header dragged onto a track in album c: the block lands after album c.
        val down = QueueList.moveLocally(rows, "header:a#1", "c1#1")
        assertThat(down.describe())
            .containsExactly("[Album b]", "B1*", "B2", "[Album c]", "C1", "[Album a]", "A1", "A2")
            .inOrder()
        assertThat(QueueList.dropMove(rows, down, "header:a#1")).isEqualTo(QueueMove(2, 3, count = 2))

        // Album c's header dragged onto album b's header: the block lands before album b.
        val up = QueueList.moveLocally(rows, "header:c#1", "header:b#1")
        assertThat(up.describe().take(2)).containsExactly("[Album c]", "C1").inOrder()
        assertThat(QueueList.dropMove(rows, up, "header:c#1")).isEqualTo(QueueMove(4, 0, count = 1))
    }

    @Test
    fun `a drop back where it started is no move`() {
        val rows = QueueList.build(albumShuffled, 0, ShuffleMode.Tracks, isPlaying = false, historyExpanded = false)
        assertThat(QueueList.dropMove(rows, rows, "b2#1")).isNull()
    }
}
