package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// Timestamps are epoch milliseconds. IDs are server-assigned strings.

/** One credited artist, from the OpenSubsonic `artists`/`albumArtists` arrays. */
data class ArtistRef(val id: String, val name: String)

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val coverArtId: String?,
    val starredAt: Long?,
    val userRating: Int?,
    val musicBrainzId: String?,
) {
    val isStarred: Boolean get() = starredAt != null
}

/** The OpenSubsonic `explicitStatus` tag. */
enum class ExplicitStatus(val apiValue: String) {
    None(""),
    Explicit("explicit"),
    Clean("clean"),
    ;

    companion object {
        fun fromApi(value: String?): ExplicitStatus = entries.firstOrNull { it.apiValue == value } ?: None
    }
}

/** A named disc of a multi-disc album. */
data class DiscTitle(val disc: Int, val title: String)

data class Album(
    val id: String,
    val name: String,
    /** Display artist: the OpenSubsonic `displayArtist`, or the legacy `artist` field. */
    val artist: String?,
    val artistId: String?,
    val artists: ImmutableList<ArtistRef>,
    val coverArtId: String?,
    val songCount: Int,
    val durationSeconds: Int,
    val year: Int?,
    val genre: String?,
    val createdAt: Long?,
    val starredAt: Long?,
    val userRating: Int?,
    val playCount: Long?,
    val playedAt: Long?,
    val isCompilation: Boolean,
    val discTitles: ImmutableList<DiscTitle>,
    val musicBrainzId: String?,
    val explicitStatus: ExplicitStatus,
) {
    val isStarred: Boolean get() = starredAt != null
}

/** Gains are in dB. Missing values are null. */
data class ReplayGain(
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
    val baseGain: Float? = null,
    val fallbackGain: Float? = null,
)

data class Song(
    val id: String,
    val title: String,
    val albumId: String?,
    val album: String?,
    /** Display artist: the OpenSubsonic `displayArtist`, or the legacy `artist` field. */
    val artist: String?,
    val artistId: String?,
    val artists: ImmutableList<ArtistRef> = persistentListOf(),
    val albumArtists: ImmutableList<ArtistRef> = persistentListOf(),
    val displayAlbumArtist: String? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArtId: String? = null,
    val durationSeconds: Int = 0,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val starredAt: Long? = null,
    val userRating: Int? = null,
    val playCount: Long? = null,
    val playedAt: Long? = null,
    val bpm: Int? = null,
    val musicBrainzId: String? = null,
    val explicitStatus: ExplicitStatus = ExplicitStatus.None,
    val replayGain: ReplayGain? = null,
) {
    val isStarred: Boolean get() = starredAt != null
}

data class Genre(val name: String, val songCount: Int, val albumCount: Int)

data class Playlist(
    val id: String,
    val name: String,
    val comment: String?,
    val owner: String?,
    val isPublic: Boolean,
    val songCount: Int,
    val durationSeconds: Int,
    val coverArtId: String?,
    val createdAt: Long?,
    val changedAt: Long?,
    val readonly: Boolean,
)

data class InternetRadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String?,
    val coverArtId: String?,
)

data class SearchResult(
    val artists: ImmutableList<Artist>,
    val albums: ImmutableList<Album>,
    val songs: ImmutableList<Song>,
    /** False while only local results are shown and the server search is still running or failed. */
    val includesServerResults: Boolean,
) {
    companion object {
        val Empty =
            SearchResult(persistentListOf(), persistentListOf(), persistentListOf(), includesServerResults = false)
    }
}

/** Which item a star or rating applies to. */
enum class MediaKind {
    Song,
    Album,
    Artist,
}

/** Server-side album lists, from `getAlbumList2`. */
enum class AlbumListType(val apiValue: String) {
    Newest("newest"),
    Recent("recent"),
    Frequent("frequent"),
    Random("random"),
    AlphabeticalByName("alphabeticalByName"),
    AlphabeticalByArtist("alphabeticalByArtist"),
    Starred("starred"),
    Highest("highest"),
}
