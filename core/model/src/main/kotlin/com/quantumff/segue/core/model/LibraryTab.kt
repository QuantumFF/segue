package com.quantumff.segue.core.model

/** The Library tabs, in DESIGN.md §4.2 order. */
enum class LibraryTab {
    Albums,
    Artists,
    Songs,
    Playlists,
    Genres,
    Downloads,
}

/** How the Albums and Artists tabs lay out their items; each tab keeps its own. */
enum class LibraryViewMode {
    Grid,
    List,
}
