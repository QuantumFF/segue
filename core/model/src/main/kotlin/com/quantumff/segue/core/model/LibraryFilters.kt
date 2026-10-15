package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList

/** Narrows the album library (DESIGN.md §4.2 filter row). Null fields do not filter. */
data class AlbumFilter(val starredOnly: Boolean = false, val genre: String? = null, val year: Int? = null) {
    val isActive: Boolean get() = starredOnly || genre != null || year != null
}

/** Extra artist details from `getArtistInfo2`. Not cached in Room; fetched when the artist page opens. */
data class ArtistInfo(
    /** Plain text: HTML tags and the trailing "Read more" link that Last.fm adds are stripped. */
    val biography: String?,
    val similarArtists: ImmutableList<Artist>,
)
