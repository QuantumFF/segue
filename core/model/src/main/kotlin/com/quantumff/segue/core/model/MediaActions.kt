package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Context menu actions for songs, albums, artists and playlists (DESIGN.md §5.7). The design
 * system's MediaContextMenu labels them; core:data's MediaActionHandler carries them out and
 * returns a [MediaActionFailure] when one fails.
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

/**
 * A [MediaAction] that failed, for the screen's snackbar. The design system words each one, so
 * every screen reports a failure alike.
 */
enum class MediaActionFailure {
    /** The songs to add to a playlist could not be fetched. */
    LoadFailed,

    /** The album or playlist to add to a playlist has no songs. */
    NothingToAdd,
    FavoriteFailed,
    DownloadFailed,
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

    /**
     * The Library's multi-selection menu (DESIGN.md §4.2): what applies to every selected [kind]
     * at once. Favorite and Download turn into their undo once [allStarred] or [allDownloaded];
     * songs are never offered "Remove download", as on their own menu.
     */
    fun selection(kind: MediaKind, allStarred: Boolean, allDownloaded: Boolean): ImmutableList<MediaAction> =
        buildList {
            add(MediaAction.Play)
            add(MediaAction.Shuffle)
            if (kind == MediaKind.Album) add(MediaAction.ShuffleAlbums)
            add(MediaAction.PlayNext)
            add(MediaAction.AddToQueue)
            if (kind != MediaKind.Artist) add(MediaAction.AddToPlaylist)
            add(if (allStarred) MediaAction.Unfavorite else MediaAction.Favorite)
            add(if (kind == MediaKind.Song) MediaAction.Download else downloadAction(allDownloaded))
        }.toImmutableList()
}

private fun downloadAction(isDownloaded: Boolean) =
    if (isDownloaded) MediaAction.RemoveDownload else MediaAction.Download
