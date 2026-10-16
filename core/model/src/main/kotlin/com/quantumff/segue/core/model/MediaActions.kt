package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Context menu actions for songs, albums, artists and playlists (DESIGN.md §5.7). The design
 * system's MediaContextMenu labels them; core:data's MediaActionHandler carries them out.
 */
enum class MediaAction {
    Play,
    Shuffle,
    ShuffleAlbums,

    /** On an album: shuffle the albums of the album's artist. */
    ShuffleArtistAlbums,
    PlayNext,
    AddToQueue,
    InstantMix,
    AddToPlaylist,
    Favorite,
    Unfavorite,
    GoToAlbum,
    GoToArtist,
    Download,
    RemoveDownload,
    Rename,
    RemoveFromPlaylist,
    Delete,
}

/** Menu orders from DESIGN.md §5.7: albums, artists and playlists put Play, Shuffle and Shuffle albums first. */
object MediaActions {
    fun song(
        isStarred: Boolean,
        hasAlbum: Boolean = true,
        hasArtist: Boolean = true,
        inPlaylist: Boolean = false,
    ): ImmutableList<MediaAction> = buildList {
        add(MediaAction.PlayNext)
        add(MediaAction.AddToQueue)
        add(MediaAction.InstantMix)
        add(MediaAction.AddToPlaylist)
        add(if (isStarred) MediaAction.Unfavorite else MediaAction.Favorite)
        if (hasAlbum) add(MediaAction.GoToAlbum)
        if (hasArtist) add(MediaAction.GoToArtist)
        add(MediaAction.Download)
        if (inPlaylist) add(MediaAction.RemoveFromPlaylist)
    }.toImmutableList()

    /** [isDownloaded]: pinned for offline, so the menu offers "Remove download" instead of "Download". */
    fun album(
        isStarred: Boolean,
        hasArtist: Boolean = true,
        isDownloaded: Boolean = false,
    ): ImmutableList<MediaAction> = buildList {
        add(MediaAction.Play)
        add(MediaAction.Shuffle)
        if (hasArtist) add(MediaAction.ShuffleArtistAlbums)
        add(MediaAction.PlayNext)
        add(MediaAction.AddToQueue)
        add(MediaAction.AddToPlaylist)
        add(if (isStarred) MediaAction.Unfavorite else MediaAction.Favorite)
        if (hasArtist) add(MediaAction.GoToArtist)
        add(downloadAction(isDownloaded))
    }.toImmutableList()

    fun artist(isStarred: Boolean, isDownloaded: Boolean = false): ImmutableList<MediaAction> = persistentListOf(
        MediaAction.Play,
        MediaAction.Shuffle,
        MediaAction.ShuffleAlbums,
        if (isStarred) MediaAction.Unfavorite else MediaAction.Favorite,
        downloadAction(isDownloaded),
    )

    fun playlist(editable: Boolean, isDownloaded: Boolean = false): ImmutableList<MediaAction> = buildList {
        add(MediaAction.Play)
        add(MediaAction.Shuffle)
        add(MediaAction.ShuffleAlbums)
        add(MediaAction.PlayNext)
        add(MediaAction.AddToQueue)
        add(downloadAction(isDownloaded))
        if (editable) {
            add(MediaAction.Rename)
            add(MediaAction.Delete)
        }
    }.toImmutableList()
}

private fun downloadAction(isDownloaded: Boolean) =
    if (isDownloaded) MediaAction.RemoveDownload else MediaAction.Download
