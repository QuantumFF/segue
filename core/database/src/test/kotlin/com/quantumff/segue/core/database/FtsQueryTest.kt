package com.quantumff.segue.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtsQueryTest {
    @Test
    fun `every word becomes a prefix term`() {
        assertThat(FtsQuery.prefixMatch("in rain")).isEqualTo("in* rain*")
    }

    @Test
    fun `punctuation and FTS operators are stripped`() {
        assertThat(
            FtsQuery.prefixMatch("""AC/DC "back" -in* (black) OR NEAR"""),
        ).isEqualTo("ac* dc* back* in* black* or* near*")
    }

    @Test
    fun `non-ASCII letters and digits are kept`() {
        assertThat(FtsQuery.prefixMatch("Röyksopp 2024")).isEqualTo("röyksopp* 2024*")
    }

    @Test
    fun `blank or symbol-only input has no query`() {
        assertThat(FtsQuery.prefixMatch("   ")).isNull()
        assertThat(FtsQuery.prefixMatch("*-\"")).isNull()
    }
}
