package com.quantumff.segue.core.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistEditTest {
    @Test
    fun unchangedListIsEmptyEdit() {
        assertThat(PlaylistEdit.between(listOf("a", "b"), listOf("a", "b")).isEmpty).isTrue()
    }

    @Test
    fun moveRewritesOnlyTheTailAfterTheCommonPrefix() {
        val edit = PlaylistEdit.between(listOf("a", "b", "c", "d"), listOf("a", "c", "b", "d"))

        assertThat(edit.indexesToRemove).containsExactly(1, 2, 3).inOrder()
        assertThat(edit.idsToAdd).containsExactly("c", "b", "d").inOrder()
    }

    @Test
    fun removalAtTheEndOnlyRemoves() {
        val edit = PlaylistEdit.between(listOf("a", "b", "c"), listOf("a", "b"))

        assertThat(edit.indexesToRemove).containsExactly(2)
        assertThat(edit.idsToAdd).isEmpty()
    }

    @Test
    fun duplicatesKeepTheirPositions() {
        val edit = PlaylistEdit.between(listOf("a", "b", "a"), listOf("a", "a", "b"))

        assertThat(edit.indexesToRemove).containsExactly(1, 2).inOrder()
        assertThat(edit.idsToAdd).containsExactly("a", "b").inOrder()
    }

    @Test
    fun biographyLosesTagsAndLastFmLink() {
        val html = "Radiohead are an English rock band. " +
            "<a href=\"https://www.last.fm/music/Radiohead\">Read more on Last.fm</a>"

        assertThat(plainBiography(html)).isEqualTo("Radiohead are an English rock band.")
    }
}
