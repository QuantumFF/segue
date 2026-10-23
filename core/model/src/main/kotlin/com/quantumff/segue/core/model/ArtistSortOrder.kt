package com.quantumff.segue.core.model

/** Sort orders for the local artist library (the Artists tab, DESIGN.md §4.2). */
enum class ArtistSortOrder {
    Name,

    /** Artists store no added date: the newest added date among their albums. */
    RecentlyAdded,
    AlbumCount,
}
