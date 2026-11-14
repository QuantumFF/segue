package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The artist's ⋮ menu carries Download, since the artist page's action row has no Download (DESIGN.md §4.4). */
class MediaActionsTest {
    @Test
    fun `an artist not pinned offers Download after Favorite`() {
        assertThat(MediaActions.artist(isStarred = false, isDownloaded = false)).containsExactly(
            MediaAction.Play,
            MediaAction.Shuffle,
            MediaAction.ShuffleAlbums,
            MediaAction.Favorite,
            MediaAction.Download,
        ).inOrder()
    }

    @Test
    fun `a pinned artist offers Remove download instead of Download`() {
        val actions = MediaActions.artist(isStarred = true, isDownloaded = true)

        assertThat(actions).contains(MediaAction.RemoveDownload)
        assertThat(actions).doesNotContain(MediaAction.Download)
        assertThat(actions.last()).isEqualTo(MediaAction.RemoveDownload)
    }

    @Test
    fun `a playlist you do not own offers the queue, Add to playlist and Download, but not Rename or Delete`() {
        assertThat(MediaActions.playlist(editable = false)).containsExactly(
            MediaAction.Play,
            MediaAction.Shuffle,
            MediaAction.ShuffleAlbums,
            MediaAction.PlayNext,
            MediaAction.AddToQueue,
            MediaAction.AddToPlaylist,
            MediaAction.Download,
        ).inOrder()
    }

    @Test
    fun `an owned playlist adds Rename and Delete`() {
        assertThat(MediaActions.playlist(editable = true, isDownloaded = true)).containsExactly(
            MediaAction.Play,
            MediaAction.Shuffle,
            MediaAction.ShuffleAlbums,
            MediaAction.PlayNext,
            MediaAction.AddToQueue,
            MediaAction.AddToPlaylist,
            MediaAction.RemoveDownload,
            MediaAction.Rename,
            MediaAction.Delete,
        ).inOrder()
    }
}
