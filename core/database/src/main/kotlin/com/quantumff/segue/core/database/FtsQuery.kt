package com.quantumff.segue.core.database

import java.text.Normalizer

/** Turns free text from a search box into a safe FTS4 `MATCH` expression, or matches it against a name in memory. */
object FtsQuery {
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val combiningMarks = Regex("\\p{M}+")

    /**
     * Every word must match a word prefix. Words are lowercased, so `OR`/`NEAR` lose their operator
     * meaning, and everything but letters and digits is dropped, so no FTS syntax gets through.
     * Null when nothing searchable is left.
     */
    fun prefixMatch(text: String): String? = text.lowercase()
        .split(nonWord)
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ") { "$it*" }

    /**
     * Whether [text] matches [query] the way an FTS4 `unicode61` column matches [prefixMatch]'s
     * expression: every word of [query] is a prefix of some word of [text], ignoring case and
     * diacritics. For names that have no FTS table, such as playlists. False when [query] has no words.
     */
    fun matchesPrefixes(text: String, query: String): Boolean {
        val queryWords = foldedWords(query).ifEmpty { return false }
        val textWords = foldedWords(text)
        return queryWords.all { word -> textWords.any { it.startsWith(word) } }
    }

    private fun foldedWords(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase()
            .split(nonWord)
            .filter { it.isNotEmpty() }
}
