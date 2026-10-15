package com.quantumff.segue

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaConstants
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.quantumff.segue.core.playback.SeguePlaybackService
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the Android Auto browse tree the way a car does: a [MediaBrowser] connected to the
 * playback service, paging through children, searching, and playing a browsed track.
 *
 * Needs a signed-in, synced library (the demo server), so it runs on a device, not in CI's unit
 * tests. Runs in the app's process, which the caller allowlist admits as the app itself. The walk
 * is logged under the tag `AutoBrowse`.
 */
@RunWith(AndroidJUnit4::class)
class AutoBrowseTreeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var browser: MediaBrowser

    @Before
    fun connect() = onMain {
        val token = SessionToken(context, ComponentName(context, SeguePlaybackService::class.java))
        browser = MediaBrowser.Builder(context, token).buildAsync().await()
    }

    @After
    fun release() = onMain {
        browser.pause()
        browser.release()
    }

    @Test
    fun browseSearchAndPlay() = onMain {
        val rootResult = browser.getLibraryRoot(null).await()
        val root = rootResult.value!!
        assertThat(root.mediaId).isEqualTo("/")
        log("root ${root.mediaId} extras=${rootResult.params?.extras}")

        val tabs = children("/")
        assertThat(tabs.map { it.mediaId }).containsExactly("/home", "/library", "/downloads").inOrder()
        assertThat(children("/home").map { it.mediaId })
            .containsExactly("/shuffle-albums", "/home/recent", "/home/added", "/home/starred").inOrder()
        assertThat(children("/library").map { it.mediaId })
            .containsExactly("/library/albums", "/library/artists", "/library/playlists", "/library/genres").inOrder()

        val firstPage = children("/library/albums", page = 0, pageSize = 5)
        val secondPage = children("/library/albums", page = 1, pageSize = 5)
        assertThat(firstPage).hasSize(5)
        assertThat(secondPage).isNotEmpty()
        assertThat(firstPage.map { it.mediaId }).containsNoneIn(secondPage.map { it.mediaId })
        val albumsNode = children("/library").first { it.mediaId == "/library/albums" }
        assertThat(albumsNode.mediaMetadata.extras!!.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE))
            .isEqualTo(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)

        val album = firstPage.first()
        val tracks = children(album.mediaId)
        assertThat(tracks).isNotEmpty()
        assertThat(tracks.all { it.mediaMetadata.isPlayable == true }).isTrue()

        val artists = children("/library/artists", pageSize = 5)
        assertThat(artists).isNotEmpty()
        children(artists.first().mediaId)
        val genres = children("/library/genres", pageSize = 5)
        assertThat(genres).isNotEmpty()
        children(genres.first().mediaId, pageSize = 5)
        children("/library/playlists", pageSize = 5)
        children("/home/recent", pageSize = 5)
        children("/home/added", pageSize = 5)
        children("/home/starred", pageSize = 5)
        children("/downloads", pageSize = 5)

        // Artwork comes from the content provider, small.
        val art = album.mediaMetadata.artworkUri!!
        assertThat(art.scheme).isEqualTo("content")
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openFileDescriptor(art, "r")!!.use { it.statSize }
        }
        assertThat(bytes).isGreaterThan(0L)
        log("artwork $art -> $bytes bytes")

        // Search, as the car's search box does.
        val query = album.mediaMetadata.title.toString()
        assertThat(browser.search(query, null).await().resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS)
        val found = browser.getSearchResult(query, 0, 20, null).await().value!!
        log("search '$query' -> ${found.map { "${it.mediaId} ${it.mediaMetadata.title}" }}")
        assertThat(found.map { it.mediaId }).contains(album.mediaId)

        // Tapping a track queues its album from that track.
        val track = tracks.last()
        browser.setMediaItem(MediaItem.Builder().setMediaId(track.mediaId).build())
        browser.prepare()
        awaitTimeline()
        log("played ${track.mediaId}: queue=${browser.mediaItemCount} current=${browser.currentMediaItem?.mediaId}")
        assertThat(browser.mediaItemCount).isEqualTo(tracks.size)
        assertThat(browser.currentMediaItemIndex).isEqualTo(tracks.lastIndex)

        // Voice: "play <album>".
        browser.setMediaItem(
            MediaItem.Builder().setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setSearchQuery(query).build(),
            ).build(),
        )
        browser.prepare()
        awaitTimeline()
        log("voice '$query': queue=${browser.mediaItemCount} current=${browser.currentMediaItem?.mediaMetadata?.title}")
        assertThat(browser.mediaItemCount).isGreaterThan(0)

        // Shuffle albums.
        browser.setMediaItem(MediaItem.Builder().setMediaId("/shuffle-albums").build())
        browser.prepare()
        awaitTimeline()
        log("shuffle albums: queue=${browser.mediaItemCount}")
        assertThat(browser.mediaItemCount).isGreaterThan(tracks.size)
    }

    private suspend fun children(parentId: String, page: Int = 0, pageSize: Int = 50): List<MediaItem> {
        val result = browser.getChildren(parentId, page, pageSize, null).await()
        assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS)
        val items = result.value!!
        log("$parentId [page $page/$pageSize] -> ${items.size}")
        items.forEach { item ->
            val flags = (if (item.mediaMetadata.isBrowsable == true) "B" else "-") +
                (if (item.mediaMetadata.isPlayable == true) "P" else "-")
            log("  $flags ${item.mediaId}  '${item.mediaMetadata.title}'  art=${item.mediaMetadata.artworkUri}")
        }
        return items
    }

    /** The service resolves browse IDs asynchronously; wait for the new queue to arrive. */
    private suspend fun awaitTimeline() = withTimeout(TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : Player.Listener {
                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && !timeline.isEmpty) {
                        browser.removeListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
            browser.addListener(listener)
            continuation.invokeOnCancellation { browser.removeListener(listener) }
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private fun onMain(block: suspend () -> Unit) = runBlocking(Dispatchers.Main) {
        withTimeout(TEST_TIMEOUT_MILLIS) { block() }
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addListener({ continuation.resume(get()) }, MoreExecutors.directExecutor())
    }

    private companion object {
        const val TAG = "AutoBrowse"
        const val TIMEOUT_MILLIS = 15_000L
        const val TEST_TIMEOUT_MILLIS = 120_000L
    }
}
