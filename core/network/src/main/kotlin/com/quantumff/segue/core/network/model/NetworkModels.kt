package com.quantumff.segue.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Subsonic JSON payloads, with the OpenSubsonic additions Segue uses. Field names follow the
// OpenSubsonic spec (opensubsonic.netlify.app) and Navidrome's responses.go. Everything that a
// server may omit has a default: Navidrome drops empty lists and zero numbers (`omitempty`), and
// plain Subsonic servers send none of the OpenSubsonic fields. Timestamps stay ISO 8601 strings;
// convert them with SubsonicDates. Unknown keys are ignored.

@Serializable
data class SubsonicEnvelope(@SerialName("subsonic-response") val response: SubsonicResponse)

@Serializable
data class SubsonicResponse(
    val status: String,
    val version: String = "",
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean = false,
    val error: NetworkError? = null,
    val license: NetworkLicense? = null,
    val musicFolders: NetworkMusicFolders? = null,
    val artists: NetworkArtistIndex? = null,
    val artist: NetworkArtist? = null,
    val album: NetworkAlbum? = null,
    val song: NetworkChild? = null,
    val albumList2: NetworkAlbumList? = null,
    val randomSongs: NetworkSongList? = null,
    val songsByGenre: NetworkSongList? = null,
    val similarSongs2: NetworkSongList? = null,
    val topSongs: NetworkSongList? = null,
    val genres: NetworkGenres? = null,
    val searchResult3: NetworkSearchResult3? = null,
    val playlists: NetworkPlaylists? = null,
    val playlist: NetworkPlaylist? = null,
    val playQueue: NetworkPlayQueue? = null,
    val playQueueByIndex: NetworkPlayQueueByIndex? = null,
    val lyricsList: NetworkLyricsList? = null,
    val internetRadioStations: NetworkInternetRadioStations? = null,
    val openSubsonicExtensions: List<NetworkExtension> = emptyList(),
)

@Serializable
data class NetworkError(val code: Int, val message: String? = null, val helpUrl: String? = null)

/** The parts of a `ping` response that identify the server. */
data class NetworkServerInfo(
    val version: String,
    val type: String?,
    val serverVersion: String?,
    val openSubsonic: Boolean,
)

@Serializable
data class NetworkExtension(val name: String, val versions: List<Int> = emptyList())

@Serializable
data class NetworkLicense(val valid: Boolean = false)

@Serializable
data class NetworkMusicFolders(val musicFolder: List<NetworkMusicFolder> = emptyList())

/** Navidrome sends a numeric `id`; the lenient decoder reads it as a string. */
@Serializable
data class NetworkMusicFolder(val id: String, val name: String = "")

/** `getArtists`. [lastModified] is epoch milliseconds. */
@Serializable
data class NetworkArtistIndex(
    val index: List<NetworkIndex> = emptyList(),
    val lastModified: Long = 0,
    val ignoredArticles: String = "",
)

/** Navidrome sends `"artist": null` for an empty index; the decoder coerces it to empty. */
@Serializable
data class NetworkIndex(val name: String, val artist: List<NetworkArtist> = emptyList())

/** ArtistID3, plus its albums when returned by `getArtist`. */
@Serializable
data class NetworkArtist(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    val userRating: Int? = null,
    val artistImageUrl: String? = null,
    val musicBrainzId: String? = null,
    val sortName: String? = null,
    val roles: List<String> = emptyList(),
    val album: List<NetworkAlbum> = emptyList(),
)

/** The `{id, name}` pairs in OpenSubsonic `artists`, `albumArtists`, and contributors. */
@Serializable
data class NetworkArtistRef(val id: String = "", val name: String)

@Serializable
data class NetworkItemGenre(val name: String)

@Serializable
data class NetworkDiscTitle(val disc: Int, val title: String = "", val coverArt: String? = null)

@Serializable
data class NetworkItemDate(val year: Int? = null, val month: Int? = null, val day: Int? = null)

@Serializable
data class NetworkContributor(val role: String, val subRole: String? = null, val artist: NetworkArtistRef)

/** AlbumID3, plus its songs when returned by `getAlbum`. */
@Serializable
data class NetworkAlbum(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val playCount: Long? = null,
    val created: String? = null,
    val starred: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    // OpenSubsonic
    val played: String? = null,
    val userRating: Int? = null,
    val genres: List<NetworkItemGenre> = emptyList(),
    val musicBrainzId: String? = null,
    val isCompilation: Boolean = false,
    val sortName: String? = null,
    val discTitles: List<NetworkDiscTitle> = emptyList(),
    val originalReleaseDate: NetworkItemDate? = null,
    val releaseDate: NetworkItemDate? = null,
    val releaseTypes: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val artists: List<NetworkArtistRef> = emptyList(),
    val displayArtist: String? = null,
    val explicitStatus: String? = null,
    val song: List<NetworkChild> = emptyList(),
)

/** OpenSubsonic ReplayGain, in dB. Navidrome sends `{}` when a file has no tags. */
@Serializable
data class NetworkReplayGain(
    val trackGain: Double? = null,
    val albumGain: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
    val baseGain: Double? = null,
    val fallbackGain: Double? = null,
)

/** A Subsonic `Child`: usually a song, or a directory when [isDir] is true. */
@Serializable
data class NetworkChild(
    val id: String,
    val parent: String? = null,
    val isDir: Boolean = false,
    val title: String = "",
    val album: String? = null,
    val artist: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val transcodedContentType: String? = null,
    val transcodedSuffix: String? = null,
    val duration: Int = 0,
    val bitRate: Int? = null,
    val path: String? = null,
    val userRating: Int? = null,
    val averageRating: Double? = null,
    val playCount: Long? = null,
    val discNumber: Int? = null,
    val created: String? = null,
    val starred: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val type: String? = null,
    // OpenSubsonic
    val played: String? = null,
    val bpm: Int? = null,
    val sortName: String? = null,
    val mediaType: String? = null,
    val musicBrainzId: String? = null,
    val genres: List<NetworkItemGenre> = emptyList(),
    val replayGain: NetworkReplayGain? = null,
    val channelCount: Int? = null,
    val samplingRate: Int? = null,
    val bitDepth: Int? = null,
    val artists: List<NetworkArtistRef> = emptyList(),
    val displayArtist: String? = null,
    val albumArtists: List<NetworkArtistRef> = emptyList(),
    val displayAlbumArtist: String? = null,
    val contributors: List<NetworkContributor> = emptyList(),
    val displayComposer: String? = null,
    val explicitStatus: String? = null,
)

@Serializable
data class NetworkAlbumList(val album: List<NetworkAlbum> = emptyList())

@Serializable
data class NetworkSongList(val song: List<NetworkChild> = emptyList())

@Serializable
data class NetworkGenres(val genre: List<NetworkGenre> = emptyList())

/** The genre name is under `value`. Navidrome omits it for the empty genre. */
@Serializable
data class NetworkGenre(val value: String = "", val songCount: Int = 0, val albumCount: Int = 0)

@Serializable
data class NetworkSearchResult3(
    val artist: List<NetworkArtist> = emptyList(),
    val album: List<NetworkAlbum> = emptyList(),
    val song: List<NetworkChild> = emptyList(),
)

@Serializable
data class NetworkPlaylists(val playlist: List<NetworkPlaylist> = emptyList())

/** Navidrome omits `public` when false. */
@Serializable
data class NetworkPlaylist(
    val id: String,
    val name: String,
    val comment: String? = null,
    val owner: String? = null,
    val public: Boolean = false,
    val songCount: Int = 0,
    val duration: Int = 0,
    val created: String? = null,
    val changed: String? = null,
    val coverArt: String? = null,
    val readonly: Boolean = false,
    val entry: List<NetworkChild> = emptyList(),
)

/** [position] is in milliseconds. Navidrome omits it when 0. */
@Serializable
data class NetworkPlayQueue(
    val entry: List<NetworkChild> = emptyList(),
    val current: String? = null,
    val position: Long = 0,
    val username: String? = null,
    val changed: String? = null,
    val changedBy: String? = null,
)

/** OpenSubsonic `indexBasedQueue`. [position] is in milliseconds. */
@Serializable
data class NetworkPlayQueueByIndex(
    val entry: List<NetworkChild> = emptyList(),
    val currentIndex: Int = 0,
    val position: Long = 0,
    val username: String? = null,
    val changed: String? = null,
    val changedBy: String? = null,
)

@Serializable
data class NetworkLyricsList(val structuredLyrics: List<NetworkStructuredLyrics> = emptyList())

/** [offset] and line starts are in milliseconds. */
@Serializable
data class NetworkStructuredLyrics(
    val lang: String = "xxx",
    val synced: Boolean = false,
    val offset: Long? = null,
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val line: List<NetworkLyricLine> = emptyList(),
)

@Serializable
data class NetworkLyricLine(val start: Long? = null, val value: String = "")

@Serializable
data class NetworkInternetRadioStations(val internetRadioStation: List<NetworkInternetRadioStation> = emptyList())

@Serializable
data class NetworkInternetRadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null,
    val coverArt: String? = null,
)
