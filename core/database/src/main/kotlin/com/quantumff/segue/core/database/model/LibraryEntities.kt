package com.quantumff.segue.core.database.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import com.quantumff.segue.core.model.ArtistRef
import com.quantumff.segue.core.model.DiscTitle

// Every row is scoped by serverId: the same Subsonic ID on two servers is two rows.
// Timestamps are epoch milliseconds.

@Entity(
    tableName = "artists",
    primaryKeys = ["serverId", "id"],
    indices = [Index("serverId", "sortName")],
)
data class ArtistEntity(
    val serverId: String,
    val id: String,
    val name: String,
    val sortName: String,
    val coverArtId: String? = null,
    val albumCount: Int = 0,
    val starredAt: Long? = null,
    val userRating: Int? = null,
    val musicBrainzId: String? = null,
)

@Entity(tableName = "artists_fts")
@Fts4(contentEntity = ArtistEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
data class ArtistFtsEntity(val name: String)

@Entity(
    tableName = "albums",
    primaryKeys = ["serverId", "id"],
    indices = [
        Index("serverId", "sortName"),
        Index("serverId", "artistId"),
        Index("serverId", "createdAt"),
        Index("serverId", "year"),
    ],
)
data class AlbumEntity(
    val serverId: String,
    val id: String,
    val name: String,
    val sortName: String,
    /** Display artist. */
    val artist: String? = null,
    val artistId: String? = null,
    val artists: ArtistRefList = ArtistRefList(),
    val coverArtId: String? = null,
    val songCount: Int = 0,
    val durationSeconds: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val createdAt: Long? = null,
    val starredAt: Long? = null,
    val userRating: Int? = null,
    val playCount: Long? = null,
    val playedAt: Long? = null,
    val isCompilation: Boolean = false,
    val discTitles: DiscTitleList = DiscTitleList(),
    val musicBrainzId: String? = null,
    val explicitStatus: String = "",
)

@Entity(tableName = "albums_fts")
@Fts4(contentEntity = AlbumEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
data class AlbumFtsEntity(val name: String, val artist: String?)

/** Credits an album to each of its OpenSubsonic `artists`, so artist pages find shared albums. */
@Entity(
    tableName = "album_artists",
    primaryKeys = ["serverId", "albumId", "artistId"],
    indices = [Index("serverId", "artistId")],
)
data class AlbumArtistCrossRef(val serverId: String, val albumId: String, val artistId: String, val position: Int)

@Entity(
    tableName = "songs",
    primaryKeys = ["serverId", "id"],
    indices = [
        Index("serverId", "albumId", "discNumber", "track"),
        Index("serverId", "artistId"),
        Index("serverId", "genre"),
        Index("serverId", "sortName"),
    ],
)
data class SongEntity(
    val serverId: String,
    val id: String,
    val title: String,
    val sortName: String,
    val albumId: String? = null,
    val album: String? = null,
    /** Display artist. */
    val artist: String? = null,
    val artistId: String? = null,
    val artists: ArtistRefList = ArtistRefList(),
    val albumArtists: ArtistRefList = ArtistRefList(),
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
    val explicitStatus: String = "",
    @Embedded(prefix = "rg_") val replayGain: ReplayGainEntity? = null,
)

data class ReplayGainEntity(
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
    val baseGain: Float? = null,
    val fallbackGain: Float? = null,
)

@Entity(tableName = "songs_fts")
@Fts4(contentEntity = SongEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
data class SongFtsEntity(val title: String, val artist: String?, val album: String?)

@Entity(tableName = "genres", primaryKeys = ["serverId", "name"])
data class GenreEntity(val serverId: String, val name: String, val songCount: Int, val albumCount: Int)

@Entity(tableName = "playlists", primaryKeys = ["serverId", "id"])
data class PlaylistEntity(
    val serverId: String,
    val id: String,
    val name: String,
    val comment: String? = null,
    val owner: String? = null,
    val isPublic: Boolean = false,
    val songCount: Int = 0,
    val durationSeconds: Int = 0,
    val coverArtId: String? = null,
    val createdAt: Long? = null,
    /** The server's `changed`. Incremental sync refetches a playlist when this differs. */
    val changedAt: Long? = null,
    val readonly: Boolean = false,
)

/** A playlist's tracks by position. The same song may appear more than once. */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["serverId", "playlistId", "position"],
    indices = [Index("serverId", "songId")],
)
data class PlaylistSongEntity(val serverId: String, val playlistId: String, val position: Int, val songId: String)

/** Membership of server-ordered album lists such as `recent` and `frequent`, for home sections. */
@Entity(tableName = "album_list_entries", primaryKeys = ["serverId", "listType", "position"])
data class AlbumListEntryEntity(val serverId: String, val listType: String, val position: Int, val albumId: String)

/** Wraps a list so it gets its own type converter. */
data class ArtistRefList(val items: List<ArtistRef> = emptyList())

data class DiscTitleList(val items: List<DiscTitle> = emptyList())
