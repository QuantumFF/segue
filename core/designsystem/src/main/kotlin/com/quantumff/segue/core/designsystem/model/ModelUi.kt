package com.quantumff.segue.core.designsystem.model

import com.quantumff.segue.core.designsystem.component.AlbumCardState
import com.quantumff.segue.core.designsystem.component.ArtistCardState
import com.quantumff.segue.core.designsystem.component.ContextMenuHeader
import com.quantumff.segue.core.designsystem.component.DownloadBadgeState
import com.quantumff.segue.core.designsystem.component.TrackRowState
import com.quantumff.segue.core.model.Album
import com.quantumff.segue.core.model.Artist
import com.quantumff.segue.core.model.CurrentSong
import com.quantumff.segue.core.model.DownloadStatus
import com.quantumff.segue.core.model.ExplicitStatus
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongAvailability

// Domain model to component state, shared by every browse screen so cards and rows read the same.

/** [subtitle] is the artist by default, or the year on artist pages (DESIGN.md §5.1). */
fun Album.toCardState(subtitle: String = artist.orEmpty(), download: DownloadBadgeState? = null): AlbumCardState =
    AlbumCardState(id = id, title = name, subtitle = subtitle, coverArtId = coverArtId, download = download)

fun Artist.toCardState(subtitle: String?): ArtistCardState =
    ArtistCardState(id = id, name = name, subtitle = subtitle, coverArtId = coverArtId)

/** A playlist as an album-style card. */
fun Playlist.toCardState(subtitle: String): AlbumCardState =
    AlbumCardState(id = id, title = name, subtitle = subtitle, coverArtId = coverArtId)

/**
 * [rowKey] identifies the row in a lazy list. It defaults to the song ID; playlists can hold a
 * song twice, so they pass a position-qualified key instead. The row is marked as playing when it
 * is [current]'s song (DESIGN.md §5.2).
 */
fun Song.toTrackRowState(
    rowKey: String = id,
    download: DownloadBadgeState? = null,
    isAvailable: Boolean = true,
    current: CurrentSong? = null,
): TrackRowState = TrackRowState(
    id = rowKey,
    title = title,
    artist = artist.orEmpty(),
    album = album,
    durationSeconds = durationSeconds.toLong(),
    trackNumber = track,
    coverArtId = coverArtId,
    explicit = explicitStatus == ExplicitStatus.Explicit,
    download = download,
    isAvailable = isAvailable,
    isCurrent = current?.isOf(this) == true,
    isPlaying = current?.isOf(this) == true && current.isPlaying,
)

/**
 * The song's row on a page that knows the library's [availability]: its download badge, whether it
 * plays (offline, only downloaded songs do), and whether it is [current]'s song.
 */
fun Song.toTrackRowState(availability: SongAvailability, current: CurrentSong?, rowKey: String = id): TrackRowState =
    toTrackRowState(
        rowKey = rowKey,
        download = availability.downloads[id]?.status?.toBadgeState(),
        isAvailable = availability.isAvailable(this),
        current = current,
    )

/** The badge for a download status (DESIGN.md §4.9). Paused and failed show no badge. */
fun DownloadStatus.toBadgeState(): DownloadBadgeState? = when (this) {
    DownloadStatus.Queued -> DownloadBadgeState.Queued
    is DownloadStatus.Downloading -> DownloadBadgeState.InProgress(progress)
    DownloadStatus.Downloaded -> DownloadBadgeState.Done
    DownloadStatus.Paused, DownloadStatus.Failed -> null
}

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
