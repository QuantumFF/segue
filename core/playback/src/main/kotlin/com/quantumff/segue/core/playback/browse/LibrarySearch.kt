package com.quantumff.segue.core.playback.browse

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaConstants
import com.quantumff.segue.core.data.repository.MediaBrowseRepository
import com.quantumff.segue.core.data.repository.SearchRepository
import com.quantumff.segue.core.model.SearchResult
import com.quantumff.segue.core.model.ShuffleMode
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull

/** Group titles for search results. */
data class SearchLabels(val artists: String, val albums: String, val songs: String)

/**
 * Search for media browsers, and voice "play <something>" (Android Auto, Assistant).
 *
 * Uses the app's search: local full-text results, replaced by the server's when it answers within
 * [serverTimeoutMillis]. Offline, the local results stand.
 */
class LibrarySearch(
    private val search: SearchRepository,
    private val repository: MediaBrowseRepository,
    private val tree: BrowseTree,
    private val labels: SearchLabels,
    private val serverTimeoutMillis: Long = DEFAULT_SERVER_TIMEOUT_MILLIS,
    private val random: Random = Random.Default,
) {
    /** Artists, albums, then songs, each group titled. */
    suspend fun results(query: String): List<MediaItem> {
        val result = find(query)
        return result.artists.map { tree.artistItem(it).inGroup(labels.artists) } +
            result.albums.map { tree.albumItem(it).inGroup(labels.albums) } +
            result.songs.map { tree.trackItem(it, BrowseId.Track(it.id, parent = null, index = 0)).inGroup(labels.songs) }
    }

    /**
     * What a voice request plays. [extras] are the platform's play-from-search extras: the focus
     * (artist, album, song) and the recognized fields. An empty query ("play some music") shuffles
     * albums. Null when nothing matched.
     */
    suspend fun playRequest(query: String, extras: Bundle): PlayRequest? {
        if (query.isBlank()) return tree.playRequest(BrowseId.ShuffleAlbums.encode())
        val focus = SearchFocus.fromMimeType(extras.getString(EXTRA_FOCUS))
        val term = when (focus) {
            SearchFocus.Artist -> extras.getString(EXTRA_ARTIST)
            SearchFocus.Album -> extras.getString(EXTRA_ALBUM)
            SearchFocus.Song -> extras.getString(EXTRA_TITLE)
            SearchFocus.Any -> null
        }?.takeIf { it.isNotBlank() } ?: query
        val request = when (val target = VoiceSearchPolicy.choose(term, focus, find(term))) {
            is VoiceTarget.Artist -> artistRequest(target.id)
            is VoiceTarget.Album -> PlayRequest(repository.albumSongsForPlayback(target.id))
            is VoiceTarget.Song -> repository.song(target.id)?.let { PlayRequest(listOf(it)) }
            null -> null
        }
        return request?.takeIf { it.songs.isNotEmpty() }
    }

    /** The artist's albums, newest first, shuffled by track. */
    private suspend fun artistRequest(artistId: String): PlayRequest {
        val songs = repository.artistAlbums(artistId)
            .take(MAX_ARTIST_ALBUMS)
            .flatMap { repository.albumSongsForPlayback(it.id) }
        // Track shuffle starts with the start item, so pick it at random too.
        val start = if (songs.isEmpty()) 0 else random.nextInt(songs.size)
        return PlayRequest(songs, startIndex = start, shuffleMode = ShuffleMode.Tracks)
    }

    private suspend fun find(query: String): SearchResult {
        if (query.isBlank()) return SearchResult.Empty
        return withTimeoutOrNull(serverTimeoutMillis) { search.search(query).toList().lastOrNull() }
            ?: search.searchLocal(query).first()
    }

    private fun MediaItem.inGroup(title: String): MediaItem {
        val extras = Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply {
            putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, title)
        }
        return buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }

    private companion object {
        const val DEFAULT_SERVER_TIMEOUT_MILLIS = 4_000L
        const val MAX_ARTIST_ALBUMS = 20

        // android.provider.MediaStore.EXTRA_MEDIA_* keys.
        const val EXTRA_FOCUS = "android.intent.extra.focus"
        const val EXTRA_ARTIST = "android.intent.extra.artist"
        const val EXTRA_ALBUM = "android.intent.extra.album"
        const val EXTRA_TITLE = "android.intent.extra.title"
    }
}
