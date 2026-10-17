package com.quantumff.segue.core.model

/** Where an instant mix starts from. */
sealed interface InstantMixSeed {
    /** [song] plays first, then songs similar to its artist. */
    data class OfSong(val song: Song) : InstantMixSeed

    /**
     * The library's song [songId], such as the one playing, as [OfSong]. There is no mix if the
     * library does not have it.
     */
    data class OfLibrarySong(val songId: String) : InstantMixSeed

    /** Songs similar to the artist. */
    data class OfArtist(val artistId: String) : InstantMixSeed
}
