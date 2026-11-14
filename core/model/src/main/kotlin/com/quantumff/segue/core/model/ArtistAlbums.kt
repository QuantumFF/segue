package com.quantumff.segue.core.model

/**
 * An artist's page's albums (DESIGN.md §4.4). The artist's own albums (they are the album artist)
 * are grouped by their primary OpenSubsonic release type into [albums], [epsAndSingles] and
 * [compilations]; the albums by others that credit them are [appearsOn]. An own album with no
 * release type, or one the app does not group, is an album, so without the extension every own
 * album is in [albums]. Each group is newest first; albums of the same year keep the order they
 * were given in, and albums without a year come last.
 */
data class ArtistAlbums(
    val albums: List<Album>,
    val epsAndSingles: List<Album>,
    val compilations: List<Album>,
    val appearsOn: List<Album>,
) {
    /** The artist's own albums: every group but [appearsOn]. */
    val own: List<Album> get() = albums + epsAndSingles + compilations

    /**
     * The order the page shows the albums in, which is also the order "Play albums" queues them in.
     * The page and the album source both read it, so the page's grouping is the queue's order.
     */
    val inPageOrder: List<Album> get() = own + appearsOn

    companion object {
        fun of(artistId: String, albums: List<Album>): ArtistAlbums {
            val newestFirst = albums.sortedWith(compareByDescending(nullsFirst()) { it.year })
            val (own, appearsOn) = newestFirst.partition { it.artistId == artistId }
            val byType = own.groupBy { it.primaryReleaseType() }
            return ArtistAlbums(
                albums = byType[ReleaseGroup.Albums].orEmpty(),
                epsAndSingles = byType[ReleaseGroup.EpsAndSingles].orEmpty(),
                compilations = byType[ReleaseGroup.Compilations].orEmpty(),
                appearsOn = appearsOn,
            )
        }

        private fun Album.primaryReleaseType(): ReleaseGroup = when (releaseTypes.firstOrNull()?.lowercase()) {
            "ep", "single" -> ReleaseGroup.EpsAndSingles
            "compilation" -> ReleaseGroup.Compilations
            else -> ReleaseGroup.Albums
        }
    }

    private enum class ReleaseGroup { Albums, EpsAndSingles, Compilations }
}
