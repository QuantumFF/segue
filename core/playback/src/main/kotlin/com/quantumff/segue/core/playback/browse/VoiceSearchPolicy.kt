package com.quantumff.segue.core.playback.browse

import com.quantumff.segue.core.model.SearchResult

/** What a voice request names, from the `android.intent.extra.focus` MIME type. */
enum class SearchFocus {
    Any,
    Artist,
    Album,
    Song,
    ;

    companion object {
        fun fromMimeType(mimeType: String?): SearchFocus = when (mimeType) {
            "vnd.android.cursor.item/artist" -> Artist
            "vnd.android.cursor.item/album" -> Album
            "vnd.android.cursor.item/audio" -> Song
            else -> Any
        }
    }
}

/** What "play <query>" plays. */
sealed interface VoiceTarget {
    data class Artist(val id: String) : VoiceTarget

    data class Album(val id: String) : VoiceTarget

    data class Song(val id: String) : VoiceTarget
}

/** Picks the best match for a voice "play <query>" from search results. */
object VoiceSearchPolicy {
    /**
     * With a focus, the top result of that kind. Otherwise an exact name match (artist, then album,
     * then song), else the top song, album, or artist. Null when nothing matched.
     */
    fun choose(query: String, focus: SearchFocus, result: SearchResult): VoiceTarget? {
        val artist = result.artists.firstOrNull()?.let { VoiceTarget.Artist(it.id) }
        val album = result.albums.firstOrNull()?.let { VoiceTarget.Album(it.id) }
        val song = result.songs.firstOrNull()?.let { VoiceTarget.Song(it.id) }
        val focused = when (focus) {
            SearchFocus.Artist -> artist
            SearchFocus.Album -> album
            SearchFocus.Song -> song
            SearchFocus.Any -> null
        }
        if (focused != null) return focused

        val wanted = normalize(query)
        return result.artists.firstOrNull { normalize(it.name) == wanted }?.let { VoiceTarget.Artist(it.id) }
            ?: result.albums.firstOrNull { normalize(it.name) == wanted }?.let { VoiceTarget.Album(it.id) }
            ?: result.songs.firstOrNull { normalize(it.title) == wanted }?.let { VoiceTarget.Song(it.id) }
            ?: song
            ?: album
            ?: artist
    }

    private fun normalize(text: String) = text.trim().lowercase()
}
