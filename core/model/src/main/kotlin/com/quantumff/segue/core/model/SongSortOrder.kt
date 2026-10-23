package com.quantumff.segue.core.model

/** Sort orders for the local song library (the Songs tab, DESIGN.md §4.2). */
enum class SongSortOrder {
    Title,

    /** By artist, then each artist's songs album by album, in track order. */
    Artist,

    /** By album, each in track order. */
    Album,

    /** The date the server added the song, newest first. */
    RecentlyAdded,

    /** Newest year first, each year's songs album by album, in track order. */
    Year,

    /** Most played first. */
    PlayCount,
}
