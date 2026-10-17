package com.quantumff.segue.feature.library

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.GridSize
import com.quantumff.segue.core.testing.data.TestData
import org.junit.Test

class DragSelectTest {
    private val albums = List(8) { LibraryItem.OfAlbum(TestData.album(id = "$it")) }

    @Test
    fun `a range grows and shrinks around its anchor, which never changes`() {
        val range = DragSelectRange(anchor = 3)

        assertThat(range.moveTo(5)).isEqualTo(RangeChange(entered = listOf(4, 5), left = emptyList()))
        assertThat(range.moveTo(4)).isEqualTo(RangeChange(entered = emptyList(), left = listOf(5)))
        // Across the anchor: the far side leaves, the near side comes in, in list order.
        assertThat(range.moveTo(1)).isEqualTo(RangeChange(entered = listOf(1, 2), left = listOf(4)))
        assertThat(range.moveTo(3)).isEqualTo(RangeChange(entered = emptyList(), left = listOf(1, 2)))
    }

    @Test
    fun `a drag from an unpicked item picks, and what it leaves goes back to how it was`() {
        val state = DragSelectState()
        state.start(index = 2, id = "2", picked = setOf("4"))

        assertThat(state.moveTo(5, albums::getOrNull)).containsExactly(
            albums[3] to true,
            albums[4] to true,
            albums[5] to true,
        ).inOrder()
        assertThat(state.moveTo(3, albums::getOrNull)).containsExactly(
            albums[4] to true,
            albums[5] to false,
        ).inOrder()
    }

    @Test
    fun `a drag from a picked item drops`() {
        val state = DragSelectState()
        state.start(index = 0, id = "0", picked = setOf("0", "1", "2"))

        assertThat(state.moveTo(2, albums::getOrNull)).containsExactly(albums[1] to false, albums[2] to false).inOrder()
        assertThat(state.moveTo(1, albums::getOrNull)).containsExactly(albums[2] to true)
    }

    @Test
    fun `items not loaded yet are skipped, and an ended drag picks nothing`() {
        val state = DragSelectState()
        state.start(index = 0, id = "0", picked = emptySet())

        assertThat(state.moveTo(2) { if (it == 1) null else albums[it] }).containsExactly(albums[2] to true)

        state.end()
        assertThat(state.isActive).isFalse()
        assertThat(state.moveTo(4, albums::getOrNull)).isEmpty()
    }

    @Test
    fun `each grid size adds or drops a column from the adaptive count`() {
        // 379 wide (a 411dp phone less its margins) fits two 150 cards with a 12 gutter.
        val columns = GridSize.entries.map { gridColumns(379.dp, 150.dp, 12.dp, it) }
        assertThat(columns).containsExactly(4, 3, 2, 1).inOrder()
        // A tablet's usual count moves the same way, and the grid never drops below one column.
        assertThat(gridColumns(800.dp, 150.dp, 12.dp, GridSize.ExtraSmall)).isEqualTo(7)
        assertThat(gridColumns(140.dp, 150.dp, 12.dp, GridSize.Large)).isEqualTo(1)
    }
}
