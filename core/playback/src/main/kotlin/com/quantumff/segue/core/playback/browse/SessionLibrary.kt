package com.quantumff.segue.core.playback.browse

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.AlbumShuffleRepository
import com.quantumff.segue.core.data.repository.MediaBrowseRepository
import com.quantumff.segue.core.data.repository.SearchRepository
import com.quantumff.segue.core.playback.R
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The library side of the media session: browse, search and play-by-ID for Android Auto and other
 * allowed media browsers. Turns [BrowseTree] and [LibrarySearch] into Media3 results. Call from the
 * session's application (main) thread; [scope] runs there too.
 */
class SessionLibrary(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: MediaBrowseRepository,
    albumShuffle: AlbumShuffleRepository,
    search: SearchRepository,
    private val callers: BrowseCallers = BrowseCallers(context),
) {
    val tree = BrowseTree(repository, albumShuffle, labels(context), artwork(context))
    private val search = LibrarySearch(search, repository, tree, searchLabels(context))
    private val searchResults = LruCache<String, List<MediaItem>>(SEARCH_CACHE_SIZE)
    private var lastHomeRefresh = 0L

    /** Only allowed callers get the library commands; everyone keeps the playback controls. */
    fun mayBrowse(controller: ControllerInfo): Boolean = callers.isAllowed(controller.packageName, controller.uid)

    fun root(session: MediaLibrarySession, browser: ControllerInfo): ListenableFuture<LibraryResult<MediaItem>> {
        if (callers.isExternal(browser.uid)) callers.grantArtwork(browser.packageName)
        refreshHomeLists(session)
        val params = LibraryParams.Builder().setExtras(BrowseTree.rootExtras()).build()
        return Futures.immediateFuture(LibraryResult.ofItem(tree.root(), params))
    }

    fun children(parentId: String, page: Int, pageSize: Int, params: LibraryParams?) = future {
        val items = tree.children(parentId, page, pageSize)
        if (items ==
            null
        ) {
            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        } else {
            LibraryResult.ofItemList(items, params)
        }
    }

    fun item(mediaId: String) = future {
        tree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
            ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }

    fun search(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = future {
        val results = searchResults(query)
        session.notifySearchResultChanged(browser, query, results.size, params)
        LibraryResult.ofVoid()
    }

    fun searchResult(query: String, page: Int, pageSize: Int, params: LibraryParams?) = future {
        if (page < 0 || pageSize <= 0) return@future LibraryResult.ofItemList(ImmutableList.of(), params)
        LibraryResult.ofItemList(searchResults(query).drop(page * pageSize).take(pageSize), params)
    }

    /** True when [items] are a browse node or a voice search, which [playRequest] resolves. */
    fun resolves(items: List<MediaItem>): Boolean {
        val item = items.singleOrNull() ?: return false
        return item.requestMetadata.searchQuery != null || BrowseId.parse(item.mediaId) != null
    }

    /** What playing a browse node, or a voice "play <query>", queues. */
    suspend fun playRequest(item: MediaItem): PlayRequest? {
        val query = item.requestMetadata.searchQuery
        return if (query != null) {
            search.playRequest(query, item.requestMetadata.extras ?: Bundle.EMPTY)
        } else {
            tree.playRequest(item.mediaId)
        }
    }

    /** Whether any of [items] is a browse node that [songsFor] expands. */
    fun hasBrowseNodes(items: List<MediaItem>) = items.any { BrowseId.parse(it.mediaId) != null }

    /** The songs [item] stands for: a browse node's songs, or null for a plain song item. */
    suspend fun songsFor(item: MediaItem) = if (BrowseId.parse(item.mediaId) == null) null else tree.songs(item.mediaId)

    private suspend fun searchResults(query: String): List<MediaItem> =
        searchResults.get(query) ?: search.results(query).also { searchResults.put(query, it) }

    /** Auto just connected: refresh the server-ordered home lists in the background, at most every few minutes. */
    private fun refreshHomeLists(session: MediaLibrarySession) {
        val now = System.currentTimeMillis()
        if (now - lastHomeRefresh < HOME_REFRESH_INTERVAL_MILLIS) return
        lastHomeRefresh = now
        scope.launch {
            try {
                repository.refreshHomeLists()
                listOf(BrowseId.RecentlyPlayed, BrowseId.RecentlyAdded).forEach {
                    session.notifyChildrenChanged(it.encode(), Int.MAX_VALUE, null)
                }
            } catch (e: RemoteException) {
                Log.i(TAG, "Home lists not refreshed", e) // Offline: the stored lists stand.
            }
        }
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> = scope.launchFuture(block)

    private companion object {
        const val TAG = "SessionLibrary"
        const val SEARCH_CACHE_SIZE = 8
        const val HOME_REFRESH_INTERVAL_MILLIS = 10 * 60_000L

        fun labels(context: Context) = BrowseLabels(
            home = context.getString(R.string.browse_home),
            library = context.getString(R.string.browse_library),
            downloads = context.getString(R.string.browse_downloads),
            shuffleAlbums = context.getString(R.string.browse_shuffle_albums),
            recentlyPlayed = context.getString(R.string.browse_recently_played),
            recentlyAdded = context.getString(R.string.browse_recently_added),
            starred = context.getString(R.string.browse_starred),
            albums = context.getString(R.string.browse_albums),
            artists = context.getString(R.string.browse_artists),
            playlists = context.getString(R.string.browse_playlists),
            genres = context.getString(R.string.browse_genres),
        )

        fun searchLabels(context: Context) = SearchLabels(
            artists = context.getString(R.string.browse_search_artists),
            albums = context.getString(R.string.browse_search_albums),
            songs = context.getString(R.string.browse_search_songs),
        )

        fun artwork(context: Context) = object : BrowseArtwork {
            override fun cover(coverArtId: String): Uri = ArtworkProvider.coverUri(context, coverArtId)

            override fun icon(icon: BrowseIcon): Uri = Uri.Builder()
                .scheme("android.resource")
                .authority(context.packageName)
                .appendPath("drawable")
                .appendPath(
                    context.resources.getResourceEntryName(
                        when (icon) {
                            BrowseIcon.Home -> R.drawable.ic_browse_home
                            BrowseIcon.Library -> R.drawable.ic_browse_library
                            BrowseIcon.Downloads -> R.drawable.ic_browse_downloads
                            BrowseIcon.Shuffle -> R.drawable.ic_browse_shuffle
                        },
                    ),
                )
                .build()
        }
    }
}
