package com.quantumff.segue.core.playback.browse

import android.os.Bundle
import com.quantumff.segue.core.data.repository.MediaBrowseRepository
import com.quantumff.segue.core.data.repository.SearchRepository
import com.quantumff.segue.core.model.SearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Search for media browsers, and voice "play <something>" (Android Auto, Assistant).
 *
 * Uses the app's search: local full-text results, replaced by the server's when it answers within
 * [serverTimeoutMillis]. Offline, the local results stand. [BrowseTree.searchResults] turns the
 * results into rows.
 */
class LibrarySearch(
    private val search: SearchRepository,
    private val repository: MediaBrowseRepository,
    private val serverTimeoutMillis: Long = DEFAULT_SERVER_TIMEOUT_MILLIS,
) {
    /** Artists, albums and songs matching [query]. */
    suspend fun find(query: String): SearchResult {
        if (query.isBlank()) return SearchResult.Empty
        return withTimeoutOrNull(serverTimeoutMillis) { search.search(query).toList().lastOrNull() }
            ?: search.searchLocal(query).first()
    }

    /**
     * What a voice request plays. [extras] are the platform's play-from-search extras: the focus
     * (artist, album, song) and the recognized fields. Null when nothing matched, and for an empty
     * query ("play some music"), which shuffles the library's albums ([SessionPlayResolver]).
     */
    suspend fun playRequest(query: String, extras: Bundle): PlayRequest? {
        if (query.isBlank()) return null
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

    /** The artist's albums, newest first, queued in shuffled track order. */
    private suspend fun artistRequest(artistId: String): PlayRequest {
        val songs = repository.artistAlbums(artistId)
            .take(MAX_ARTIST_ALBUMS)
            .flatMap { repository.albumSongsForPlayback(it.id) }
        return PlayRequest(songs, shuffle = true)
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
