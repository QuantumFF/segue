package com.quantumff.segue.core.database.model

import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ExplicitStatus
import com.quantumff.segue.core.model.Genre
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.model.ServerCapabilities
import com.quantumff.segue.core.model.Song
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

fun ArtistEntity.asExternalModel() = Artist(
    id = id,
    name = name,
    albumCount = albumCount,
    coverArtId = coverArtId,
    starredAt = starredAt,
    userRating = userRating,
    musicBrainzId = musicBrainzId,
)

fun AlbumEntity.asExternalModel() = Album(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    artists = artists.items.toImmutableList(),
    coverArtId = coverArtId,
    songCount = songCount,
    durationSeconds = durationSeconds,
    year = year,
    genre = genre,
    createdAt = createdAt,
    starredAt = starredAt,
    userRating = userRating,
    playCount = playCount,
    playedAt = playedAt,
    isCompilation = isCompilation,
    discTitles = discTitles.items.toImmutableList(),
    musicBrainzId = musicBrainzId,
    explicitStatus = ExplicitStatus.fromApi(explicitStatus),
)

fun SongEntity.asExternalModel() = Song(
    id = id,
    title = title,
    albumId = albumId,
    album = album,
    artist = artist,
    artistId = artistId,
    artists = artists.items.toImmutableList(),
    albumArtists = albumArtists.items.toImmutableList(),
    displayAlbumArtist = displayAlbumArtist,
    track = track,
    discNumber = discNumber,
    year = year,
    genre = genre,
    coverArtId = coverArtId,
    durationSeconds = durationSeconds,
    bitRate = bitRate,
    suffix = suffix,
    contentType = contentType,
    sizeBytes = sizeBytes,
    starredAt = starredAt,
    userRating = userRating,
    playCount = playCount,
    playedAt = playedAt,
    bpm = bpm,
    musicBrainzId = musicBrainzId,
    explicitStatus = ExplicitStatus.fromApi(explicitStatus),
    replayGain = replayGain?.let {
        ReplayGain(it.trackGain, it.albumGain, it.trackPeak, it.albumPeak, it.baseGain, it.fallbackGain)
    },
)

fun GenreEntity.asExternalModel() = Genre(name = name, songCount = songCount, albumCount = albumCount)

fun PlaylistEntity.asExternalModel() = Playlist(
    id = id,
    name = name,
    comment = comment,
    owner = owner,
    isPublic = isPublic,
    songCount = songCount,
    durationSeconds = durationSeconds,
    coverArtId = coverArtId,
    createdAt = createdAt,
    changedAt = changedAt,
    readonly = readonly,
)

fun ServerCapabilitiesEntity.asExternalModel() = ServerCapabilities(
    apiVersion = apiVersion,
    openSubsonic = openSubsonic,
    serverType = serverType,
    serverVersion = serverVersion,
    extensions = extensions.versions.mapValues { it.value.toImmutableList() }.toImmutableMap(),
)
