package com.quantumff.segue.feature.home

import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.ExplicitStatus
import kotlinx.collections.immutable.persistentListOf

/** Sample albums for previews and screenshot tests. */
internal val PreviewAlbums = persistentListOf(
    previewAlbum("1", "In Rainbows", "Radiohead", 2007, 10),
    previewAlbum("2", "Kid A", "Radiohead", 2000, 10),
    previewAlbum("3", "Vespertine", "Björk", 2001, 12),
    previewAlbum("4", "Blue Lines", "Massive Attack", 1991, 9),
)

private fun previewAlbum(id: String, name: String, artist: String, year: Int, songs: Int) = Album(
    id = id,
    name = name,
    artist = artist,
    artistId = "ar-$artist",
    artists = persistentListOf(),
    coverArtId = null,
    songCount = songs,
    durationSeconds = songs * 240,
    year = year,
    genre = "Alternative",
    createdAt = null,
    starredAt = null,
    userRating = null,
    playCount = null,
    playedAt = null,
    isCompilation = false,
    discTitles = persistentListOf(),
    musicBrainzId = null,
    explicitStatus = ExplicitStatus.None,
)
