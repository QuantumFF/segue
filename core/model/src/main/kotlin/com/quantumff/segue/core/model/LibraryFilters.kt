package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList

/**
 * Narrows the album library (DESIGN.md §4.2 filter row). Null fields do not filter.
 * [starred] true keeps favorites and false keeps the rest; [downloaded] true keeps albums with at
 * least one downloaded song and false keeps albums with none.
 */
data class AlbumFilter(
    val starred: Boolean? = null,
    val genre: String? = null,
    val year: Int? = null,
    val downloaded: Boolean? = null,
) {
    val isActive: Boolean get() = starred != null || genre != null || year != null || downloaded != null
}

/**
 * Narrows the artist library (the Artists tab's Favorites and Downloaded chips). Null fields do
 * not filter. [starred] true keeps favorites and false keeps the rest; [downloaded] true keeps
 * artists with a downloaded song, as its track artist or its album's artist, and false keeps the rest.
 */
data class ArtistFilter(val starred: Boolean? = null, val downloaded: Boolean? = null) {
    val isActive: Boolean get() = starred != null || downloaded != null
}

/** Extra artist details from `getArtistInfo2`. Not cached in Room; fetched when the artist page opens. */
data class ArtistInfo(
    /** Plain text: HTML tags and the trailing "Read more" link that Last.fm adds are stripped. */
    val biography: String?,
    val similarArtists: ImmutableList<Artist>,
)
