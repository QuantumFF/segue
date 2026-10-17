package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentSearchesTest {
    @Test
    fun `a query is trimmed and moved to the front`() {
        assertThat(RecentSearches.add(listOf("abba", "blur"), "  blur ")).containsExactly("blur", "abba").inOrder()
    }

    @Test
    fun `an earlier entry differing only in case is replaced`() {
        assertThat(RecentSearches.add(listOf("Abba", "blur"), "ABBA")).containsExactly("ABBA", "blur").inOrder()
    }

    @Test
    fun `a blank query changes nothing`() {
        assertThat(RecentSearches.add(listOf("abba"), "   ")).containsExactly("abba")
    }

    @Test
    fun `line breaks become spaces`() {
        assertThat(RecentSearches.add(emptyList(), "a\nb")).containsExactly("a b")
    }

    @Test
    fun `at most ten are kept, oldest dropped`() {
        val full = (1..10).map { "q$it" }

        val updated = RecentSearches.add(full, "new")

        assertThat(updated).hasSize(RecentSearches.MAX)
        assertThat(updated.first()).isEqualTo("new")
        assertThat(updated).doesNotContain("q10")
    }

    @Test
    fun `remove drops the exact entry`() {
        assertThat(RecentSearches.remove(listOf("abba", "Abba"), "abba")).containsExactly("Abba")
    }
}
