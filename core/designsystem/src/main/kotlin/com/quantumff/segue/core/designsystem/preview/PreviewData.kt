package com.quantumff.segue.core.designsystem.preview

import com.quantumff.segue.core.designsystem.component.AlbumCardState
import com.quantumff.segue.core.designsystem.component.ArtistCardState
import com.quantumff.segue.core.designsystem.component.DownloadBadgeState
import com.quantumff.segue.core.designsystem.component.MiniPlayerState
import com.quantumff.segue.core.designsystem.component.TrackRowState
import kotlinx.collections.immutable.persistentListOf

/** Sample UI state for previews and screenshot tests. Not for production code paths. */
object PreviewData {
    val album = AlbumCardState(
        id = "al-1",
        title = "In Rainbows",
        subtitle = "Radiohead",
        coverArtId = null,
        download = DownloadBadgeState.Done,
    )

    val albums = persistentListOf(
        album,
        AlbumCardState(id = "al-2", title = "Kid A", subtitle = "Radiohead", coverArtId = null),
        AlbumCardState(id = "al-3", title = "Blonde", subtitle = "Frank Ocean", coverArtId = null),
        AlbumCardState(
            id = "al-4",
            title = "To Pimp a Butterfly",
            subtitle = "Kendrick Lamar",
            coverArtId = null,
            download = DownloadBadgeState.InProgress(progress = 0.4f),
        ),
        AlbumCardState(id = "al-5", title = "Vespertine", subtitle = "Björk", coverArtId = null),
    )

    val artist = ArtistCardState(id = "ar-1", name = "Radiohead", subtitle = "9 albums", coverArtId = null)

    val tracks = persistentListOf(
        TrackRowState(
            id = "t-1",
            title = "15 Step",
            artist = "Radiohead",
            album = "In Rainbows",
            durationSeconds = 237,
            trackNumber = 1,
        ),
        TrackRowState(
            id = "t-2",
            title = "Bodysnatchers",
            artist = "Radiohead",
            album = "In Rainbows",
            durationSeconds = 242,
            trackNumber = 2,
            download = DownloadBadgeState.Queued,
        ),
        TrackRowState(
            id = "t-3",
            title = "Nude",
            artist = "Radiohead",
            album = "In Rainbows",
            durationSeconds = 255,
            trackNumber = 3,
            isCurrent = true,
            isPlaying = true,
        ),
        TrackRowState(
            id = "t-4",
            title = "Weird Fishes/Arpeggi",
            artist = "Radiohead",
            album = "In Rainbows",
            durationSeconds = 318,
            trackNumber = 4,
            explicit = true,
            isAvailable = false,
        ),
    )

    val miniPlayer = MiniPlayerState(
        title = "Nude",
        artist = "Radiohead",
        coverArtId = null,
        isPlaying = true,
    )
}
