package com.quantumff.segue.core.database

/** Turns free text from a search box into a safe FTS4 `MATCH` expression. */
object FtsQuery {
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

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
}
