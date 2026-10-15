package com.quantumff.segue.core.designsystem.model

import com.quantumff.segue.core.designsystem.component.AlbumCardState
import com.quantumff.segue.core.designsystem.component.ArtistCardState
import com.quantumff.segue.core.designsystem.component.ContextMenuHeader
import com.quantumff.segue.core.designsystem.component.TrackRowState
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.ExplicitStatus
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song

// Domain model to component state, shared by every browse screen so cards and rows read the same.

/** [subtitle] is the artist by default, or the year on artist pages (DESIGN.md §5.1). */
fun Album.toCardState(subtitle: String = artist.orEmpty()): AlbumCardState =
    AlbumCardState(id = id, title = name, subtitle = subtitle, coverArtId = coverArtId)

fun Artist.toCardState(subtitle: String?): ArtistCardState =
    ArtistCardState(id = id, name = name, subtitle = subtitle, coverArtId = coverArtId)

/** A playlist as an album-style card. */
fun Playlist.toCardState(subtitle: String): AlbumCardState =
    AlbumCardState(id = id, title = name, subtitle = subtitle, coverArtId = coverArtId)

/**
 * [rowKey] identifies the row in a lazy list. It defaults to the song ID; playlists can hold a
 * song twice, so they pass a position-qualified key instead.
 */
fun Song.toTrackRowState(rowKey: String = id): TrackRowState = TrackRowState(
    id = rowKey,
    title = title,
    artist = artist.orEmpty(),
    album = album,
    durationSeconds = durationSeconds.toLong(),
    trackNumber = track,
    coverArtId = coverArtId,
    explicit = explicitStatus == ExplicitStatus.Explicit,
)

fun Song.toMenuHeader(): ContextMenuHeader = ContextMenuHeader(
    title = title,
    subtitle = listOfNotNull(artist, album).joinToString(" · ").ifEmpty { null },
    coverArtId = coverArtId,
)

fun Album.toMenuHeader(): ContextMenuHeader =
    ContextMenuHeader(title = name, subtitle = artist, coverArtId = coverArtId)

fun Artist.toMenuHeader(): ContextMenuHeader =
    ContextMenuHeader(title = name, subtitle = null, coverArtId = coverArtId, circularArt = true)

fun Playlist.toMenuHeader(): ContextMenuHeader =
    ContextMenuHeader(title = name, subtitle = owner, coverArtId = coverArtId)
