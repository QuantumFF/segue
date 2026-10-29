package com.quantumff.segue.core.playback.service

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.model.DownloadedLibrary
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.QueueSource
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.playback.media.toMediaItem
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueEditorTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
    private val player = SeguePlayer(exo)

    /** Null online; offline the downloaded songs. */
    private var downloaded: Set<String>? = null
    private val editor =
        QueueEditor(player, offline = { downloaded?.let { DownloadedLibrary(songIds = it) } }, Random(1))

    @After
    fun tearDown() = exo.release()

    private fun items(vararg ids: String) = ids.map {
        Song(id = it, title = it, albumId = null, album = null, artist = null, artistId = null).toMediaItem()
    }

    private fun queueIds() = List(exo.mediaItemCount) { exo.getMediaItemAt(it).mediaId }

    @Test
    fun `replace sets the queue from the start song and plays`() = runTest {
        player.setMediaItems(items("x"))

        assertThat(editor.queue(items("a", "b", "c"), QueuePlacement.Replace, startIndex = 1)).isEqualTo(3)

        assertThat(queueIds()).containsExactly("a", "b", "c").inOrder()
        assertThat(exo.currentMediaItemIndex).isEqualTo(1)
        assertThat(exo.playWhenReady).isTrue()
        assertThat(exo.playbackState).isNotEqualTo(Player.STATE_IDLE)
    }

    @Test
    fun `next inserts right after the current song, end appends`() = runTest {
        player.setMediaItems(items("x1", "x2", "x3"), 1, 0)

        editor.queue(items("n1", "n2"), QueuePlacement.Next)
        editor.queue(items("e1"), QueuePlacement.End)

        assertThat(queueIds()).containsExactly("x1", "x2", "n1", "n2", "x3", "e1").inOrder()
        assertThat(exo.currentMediaItemIndex).isEqualTo(1)
        assertThat(exo.playWhenReady).isFalse()
    }

    @Test
    fun `next or end into an empty queue sets and prepares it without playing`() = runTest {
        editor.queue(items("a", "b"), QueuePlacement.Next)

        assertThat(queueIds()).containsExactly("a", "b").inOrder()
        assertThat(exo.playbackState).isNotEqualTo(Player.STATE_IDLE)
        assertThat(exo.playWhenReady).isFalse()

        player.clearMediaItems()
        editor.queue(items("c"), QueuePlacement.End)
        assertThat(queueIds()).containsExactly("c")
    }

    @Test
    fun `place reports where the items landed`() {
        player.setMediaItems(items("x1", "x2", "x3"), 0, 0)

        assertThat(editor.place(items("n"), QueuePlacement.Next)).isEqualTo(1)
        assertThat(editor.place(items("e"), QueuePlacement.End)).isEqualTo(4)
        assertThat(editor.place(items("r"), QueuePlacement.Replace)).isEqualTo(0)
    }

    @Test
    fun `online a start past the end plays the last song`() = runTest {
        editor.queue(items("a", "b", "c"), QueuePlacement.Replace, startIndex = 7)

        assertThat(exo.currentMediaItemIndex).isEqualTo(2)
    }

    @Test
    fun `an empty request is Media3's own, a play intent of nothing queues nothing`() = runTest {
        // A set of nothing clears the queue; an add of nothing changes nothing. Neither fails.
        val empty = editor.playable(emptyList(), startIndex = 0, startPositionMs = 0)!!
        assertThat(empty.mediaItems).isEmpty()
        downloaded = emptySet()
        assertThat(editor.playable(emptyList())?.mediaItems).isEmpty()

        downloaded = null
        player.setMediaItems(items("x"))
        assertThat(editor.queue(emptyList(), QueuePlacement.Replace)).isEqualTo(0)
        assertThat(queueIds()).containsExactly("x")
    }

    @Test
    fun `shuffle queues every song in a fresh order, from the first`() = runTest {
        val ids = (1..30).map { "s$it" }

        editor.queue(items(*ids.toTypedArray()), QueuePlacement.Replace, startIndex = 7, shuffle = true)

        assertThat(queueIds()).containsExactlyElementsIn(ids)
        assertThat(queueIds()).isNotEqualTo(ids)
        assertThat(exo.currentMediaItemIndex).isEqualTo(0)
    }

    @Test
    fun `offline only downloaded songs are queued`() = runTest {
        downloaded = setOf("b", "d")
        player.setMediaItems(items("x"))

        editor.queue(items("a", "b", "c", "d"), QueuePlacement.End)

        assertThat(queueIds()).containsExactly("x", "b", "d").inOrder()
    }

    @Test
    fun `offline the start moves to the next downloaded song, else the first`() = runTest {
        downloaded = setOf("a", "d")

        editor.queue(items("a", "b", "c", "d"), QueuePlacement.Replace, startIndex = 1)
        assertThat(queueIds()).containsExactly("a", "d").inOrder()
        assertThat(exo.currentMediaItemIndex).isEqualTo(1)

        editor.queue(items("a", "b", "c", "d", "e"), QueuePlacement.Replace, startIndex = 4)
        assertThat(exo.currentMediaItemIndex).isEqualTo(0)
    }

    @Test
    fun `offline the count queued is of the downloaded songs only`() = runTest {
        downloaded = setOf("a", "c")
        player.setMediaItems(items("x"))

        assertThat(editor.queue(items("a", "b", "c"), QueuePlacement.End)).isEqualTo(2)

        assertThat(queueIds()).containsExactly("x", "a", "c").inOrder()
    }

    @Test
    fun `nothing downloaded offline leaves the queue alone`() = runTest {
        downloaded = emptySet()
        player.setMediaItems(items("x"))

        assertThat(editor.queue(items("a", "b"), QueuePlacement.Replace)).isEqualTo(0)
        assertThat(editor.queue(emptyList(), QueuePlacement.End)).isEqualTo(0)

        assertThat(queueIds()).containsExactly("x")
    }

    @Test
    fun `an undone removal comes back where it was, offline too`() {
        downloaded = emptySet()
        player.setMediaItems(items("a", "c"))
        val removed = items("b").single().buildUpon().setUri(null as String?).build()

        editor.putBack(removed, 1)
        editor.putBack(items("z").single(), 9)

        assertThat(queueIds()).containsExactly("a", "b", "c", "z").inOrder()
        assertThat(exo.getMediaItemAt(1).localConfiguration).isNotNull()
    }

    @Test
    fun `playable keeps a start position only while the start song stays`() = runTest {
        val stripped = items("a", "b", "c").map { it.buildUpon().setUri(null as String?).build() }

        val online = editor.playable(stripped, startIndex = 2, startPositionMs = 5_000)!!
        assertThat(online.startIndex).isEqualTo(2)
        assertThat(online.startPositionMs).isEqualTo(5_000)
        // Controllers may strip the URI; the session needs it back to play.
        assertThat(online.mediaItems.all { it.localConfiguration != null }).isTrue()

        downloaded = setOf("a", "b")
        val moved = editor.playable(stripped, startIndex = 2, startPositionMs = 5_000)!!
        assertThat(moved.mediaItems.map { it.mediaId }).containsExactly("a", "b").inOrder()
        assertThat(moved.startIndex).isEqualTo(0)
        assertThat(moved.startPositionMs).isEqualTo(0)

        // Media3's "reset position" stays unset.
        val reset = editor.playable(stripped, C.INDEX_UNSET, C.TIME_UNSET)!!
        assertThat(reset.startIndex).isEqualTo(C.INDEX_UNSET)
        assertThat(reset.startPositionMs).isEqualTo(C.TIME_UNSET)
    }

    @Test
    fun `a replaced queue takes its play intent's source, play next and add to queue keep it`() = runTest {
        val album = QueueSource.Album("al", "In Rainbows")
        assertThat(player.queueSource.value).isEqualTo(QueueSource.Unknown)

        editor.queue(items("a", "b"), QueuePlacement.Replace, source = album)
        assertThat(player.queueSource.value).isEqualTo(album)

        editor.queue(items("n"), QueuePlacement.Next, source = QueueSource.Playlist("p", "Road trip"))
        editor.queue(items("e"), QueuePlacement.End, source = QueueSource.Unknown)
        assertThat(player.queueSource.value).isEqualTo(album)

        editor.place(items("r"), QueuePlacement.Replace, source = QueueSource.Library(shuffled = true))
        assertThat(player.queueSource.value).isEqualTo(QueueSource.Library(shuffled = true))
    }

    @Test
    fun `a play intent that queues nothing keeps the source`() = runTest {
        val album = QueueSource.Album("al", "In Rainbows")
        editor.queue(items("a"), QueuePlacement.Replace, source = album)

        downloaded = emptySet()
        editor.queue(items("b"), QueuePlacement.Replace, source = QueueSource.Genre("Rock"))

        assertThat(player.queueSource.value).isEqualTo(album)
    }
}
