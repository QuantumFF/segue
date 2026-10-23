package com.quantumff.segue.core.data.model

import com.quantumff.segue.core.database.model.AlbumArtistCrossRef
import com.quantumff.segue.core.database.model.AlbumEntity
import com.quantumff.segue.core.database.model.AlbumGenreCrossRef
import com.quantumff.segue.core.database.model.ArtistEntity
import com.quantumff.segue.core.database.model.ArtistRefList
import com.quantumff.segue.core.database.model.DiscTitleList
import com.quantumff.segue.core.database.model.GenreEntity
import com.quantumff.segue.core.database.model.PlaylistEntity
import com.quantumff.segue.core.database.model.ReplayGainEntity
import com.quantumff.segue.core.database.model.SongEntity
import com.quantumff.segue.core.model.ArtistRef
import com.quantumff.segue.core.model.DiscTitle
import com.quantumff.segue.core.network.SubsonicDates
import com.quantumff.segue.core.network.model.NetworkAlbum
import com.quantumff.segue.core.network.model.NetworkArtist
import com.quantumff.segue.core.network.model.NetworkArtistRef
import com.quantumff.segue.core.network.model.NetworkChild
import com.quantumff.segue.core.network.model.NetworkGenre
import com.quantumff.segue.core.network.model.NetworkPlaylist
import com.quantumff.segue.core.network.model.NetworkReplayGain

// Network payloads to Room rows. Servers use 0 and "" for "unknown"; those become null here.

internal fun NetworkArtist.asEntity(serverId: String) = ArtistEntity(
    serverId = serverId,
    id = id,
    name = name,
    sortName = sortKey(sortName, name),
    coverArtId = coverArt.nullIfBlank(),
    albumCount = albumCount,
    starredAt = SubsonicDates.toEpochMillis(starred),
    userRating = userRating.nullIfZero(),
    musicBrainzId = musicBrainzId.nullIfBlank(),
)

internal fun NetworkAlbum.asEntity(serverId: String) = AlbumEntity(
    serverId = serverId,
    id = id,
    name = name,
    sortName = sortKey(sortName, name),
    artist = displayArtist.nullIfBlank() ?: artist.nullIfBlank(),
    artistId = artistId.nullIfBlank(),
    artists = artists.asRefList(),
    coverArtId = coverArt.nullIfBlank(),
    songCount = songCount,
    durationSeconds = duration,
    year = year.nullIfZero(),
    genre = genre.nullIfBlank(),
    createdAt = SubsonicDates.toEpochMillis(created),
    starredAt = SubsonicDates.toEpochMillis(starred),
    userRating = userRating.nullIfZero(),
    playCount = playCount,
    playedAt = SubsonicDates.toEpochMillis(played),
    isCompilation = isCompilation,
    discTitles = DiscTitleList(discTitles.map { DiscTitle(it.disc, it.title) }),
    musicBrainzId = musicBrainzId.nullIfBlank(),
    explicitStatus = explicitStatus.orEmpty(),
)

/** One credit per OpenSubsonic album artist, for finding albums from an artist's page. */
internal fun NetworkAlbum.artistCrossRefs(serverId: String): List<AlbumArtistCrossRef> = artists
    .filter { it.id.isNotBlank() }
    .distinctBy { it.id }
    .mapIndexed { index, ref -> AlbumArtistCrossRef(serverId, albumId = id, artistId = ref.id, position = index) }

/** One tag per OpenSubsonic album genre; the lone Subsonic `genre` when the server sends no list. */
internal fun NetworkAlbum.genreCrossRefs(serverId: String): List<AlbumGenreCrossRef> =
    genres.map { it.name }.ifEmpty { listOfNotNull(genre) }
        .filter { it.isNotBlank() }
        .distinct()
        .map { AlbumGenreCrossRef(serverId, albumId = id, genre = it) }

internal fun NetworkChild.asEntity(serverId: String): SongEntity {
    val shownArtist = displayArtist.nullIfBlank() ?: artist.nullIfBlank()
    val albumName = album.nullIfBlank()
    return SongEntity(
        serverId = serverId,
        id = id,
        title = title,
        sortName = sortKey(sortName, title),
        albumId = albumId.nullIfBlank(),
        album = albumName,
        artist = shownArtist,
        artistId = artistId.nullIfBlank(),
        artists = artists.asRefList(),
        albumArtists = albumArtists.asRefList(),
        displayAlbumArtist = displayAlbumArtist.nullIfBlank(),
        track = track.nullIfZero(),
        discNumber = discNumber.nullIfZero(),
        year = year.nullIfZero(),
        genre = genre.nullIfBlank(),
        // A song without its own art ID shows its album's: `getCoverArt` takes an album ID too.
        coverArtId = coverArt.nullIfBlank() ?: albumId.nullIfBlank(),
        durationSeconds = duration,
        bitRate = bitRate.nullIfZero(),
        suffix = suffix.nullIfBlank(),
        contentType = contentType.nullIfBlank(),
        sizeBytes = size,
        starredAt = SubsonicDates.toEpochMillis(starred),
        userRating = userRating.nullIfZero(),
        playCount = playCount,
        playedAt = SubsonicDates.toEpochMillis(played),
        bpm = bpm.nullIfZero(),
        musicBrainzId = musicBrainzId.nullIfBlank(),
        explicitStatus = explicitStatus.orEmpty(),
        replayGain = replayGain?.asEntity(),
        createdAt = SubsonicDates.toEpochMillis(created),
        artistSortName = shownArtist?.lowercase(),
        albumSortName = albumName?.lowercase(),
    )
}

/** Songs only: `getPlaylist` and queue entries can include directories on folder-based servers. */
internal fun List<NetworkChild>.songEntities(serverId: String): List<SongEntity> =
    filterNot { it.isDir }.map { it.asEntity(serverId) }

internal fun NetworkGenre.asEntity(serverId: String) =
    GenreEntity(serverId = serverId, name = value, songCount = songCount, albumCount = albumCount)

internal fun NetworkPlaylist.asEntity(serverId: String) = PlaylistEntity(
    serverId = serverId,
    id = id,
    name = name,
    comment = comment.nullIfBlank(),
    owner = owner.nullIfBlank(),
    isPublic = public,
    songCount = songCount,
    durationSeconds = duration,
    coverArtId = coverArt.nullIfBlank(),
    createdAt = SubsonicDates.toEpochMillis(created),
    changedAt = SubsonicDates.toEpochMillis(changed),
    readonly = readonly,
)

private fun NetworkReplayGain.asEntity(): ReplayGainEntity? {
    val entity = ReplayGainEntity(
        trackGain = trackGain?.toFloat(),
        albumGain = albumGain?.toFloat(),
        trackPeak = trackPeak?.toFloat(),
        albumPeak = albumPeak?.toFloat(),
        baseGain = baseGain?.toFloat(),
        fallbackGain = fallbackGain?.toFloat(),
    )
    return entity.takeUnless { it == ReplayGainEntity() }
}

private fun List<NetworkArtistRef>.asRefList() = ArtistRefList(map { ArtistRef(it.id, it.name) })

private fun sortKey(sortName: String?, name: String): String = (sortName.nullIfBlank() ?: name).lowercase()

private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

private fun Int?.nullIfZero(): Int? = this?.takeIf { it != 0 }
