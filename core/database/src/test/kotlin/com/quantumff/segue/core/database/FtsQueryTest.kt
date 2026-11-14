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

    @Test
    fun `a name matches when every word is a word prefix, in any order`() {
        assertThat(FtsQuery.matchesPrefixes("Rainy Day Mix", "mix rain")).isTrue()
        assertThat(FtsQuery.matchesPrefixes("Rainy Day Mix", "ain")).isFalse()
        assertThat(FtsQuery.matchesPrefixes("Rainy Day Mix", "rain night")).isFalse()
    }

    @Test
    fun `name matching ignores case, punctuation and diacritics like unicode61`() {
        assertThat(FtsQuery.matchesPrefixes("Röyksopp's Best", "ROYK best")).isTrue()
        assertThat(FtsQuery.matchesPrefixes("Royksopp", "röyk")).isTrue()
        assertThat(FtsQuery.matchesPrefixes("AC/DC Live", "dc")).isTrue()
    }

    @Test
    fun `a query with no words matches no name`() {
        assertThat(FtsQuery.matchesPrefixes("Anything", " *- ")).isFalse()
    }
}
