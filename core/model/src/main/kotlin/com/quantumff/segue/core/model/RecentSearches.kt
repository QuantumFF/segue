package com.quantumff.segue.core.model

/**
 * The recent-searches rule, newest first. Pure, so the stored list and test fakes cannot drift
 * apart.
 */
object RecentSearches {
    const val MAX = 10

    /** Separates the entries where the list is stored as one string; an entry never contains it. */
    const val SEPARATOR = "\n"

    /**
     * Moves [query], trimmed and on one line, to the front of [current], dropping any entry that
     * differs only in case and keeping at most [MAX]. A blank query leaves [current] unchanged.
     */
    fun add(current: List<String>, query: String): List<String> {
        val clean = query.trim().replace(SEPARATOR, " ")
        if (clean.isEmpty()) return current
        return (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) })
            .filter { it.isNotEmpty() }
            .take(MAX)
    }

    fun remove(current: List<String>, query: String): List<String> = current.filterNot { it == query }
}
