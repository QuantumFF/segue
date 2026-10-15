package com.quantumff.segue.core.playback.browse

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A node of the media browse tree (Android Auto), and its media ID.
 *
 * Encoded IDs are paths that start with `/`, which song IDs never do, so the session can tell a
 * browse ID from a plain song ID that the app's own controller sends. Variable parts are
 * URL-encoded, so names with `/` stay one segment.
 */
sealed interface BrowseId {
    fun encode(): String

    data object Root : Fixed("/")

    data object Home : Fixed("/home")

    data object Library : Fixed("/library")

    data object Downloads : Fixed("/downloads")

    /** Playable: album shuffle of the whole library. */
    data object ShuffleAlbums : Fixed("/shuffle-albums")

    data object RecentlyPlayed : Fixed("/home/recent")

    data object RecentlyAdded : Fixed("/home/added")

    data object Starred : Fixed("/home/starred")

    data object Albums : Fixed("/library/albums")

    data object Artists : Fixed("/library/artists")

    data object Playlists : Fixed("/library/playlists")

    data object Genres : Fixed("/library/genres")

    data class Album(val id: String) : BrowseId {
        override fun encode() = path(ALBUM, id)
    }

    data class Artist(val id: String) : BrowseId {
        override fun encode() = path(ARTIST, id)
    }

    data class Playlist(val id: String) : BrowseId {
        override fun encode() = path(PLAYLIST, id)
    }

    data class Genre(val name: String) : BrowseId {
        override fun encode() = path(GENRE, name)
    }

    /**
     * A song listed at [index] of [parent]'s tracks (null: a search result). Playing it queues the
     * whole list from there. The index keeps a song listed twice (a playlist) unique.
     */
    data class Track(val songId: String, val parent: BrowseId?, val index: Int) : BrowseId {
        override fun encode() = path(TRACK, songId, index.toString(), parent?.encode().orEmpty())
    }

    sealed class Fixed(private val path: String) : BrowseId {
        override fun encode() = path
    }

    companion object {
        private const val ALBUM = "album"
        private const val ARTIST = "artist"
        private const val PLAYLIST = "playlist"
        private const val GENRE = "genre"
        private const val TRACK = "track"

        private val fixed: Map<String, BrowseId> by lazy {
            listOf(
                Root, Home, Library, Downloads, ShuffleAlbums, RecentlyPlayed, RecentlyAdded, Starred,
                Albums, Artists, Playlists, Genres,
            ).associateBy { it.encode() }
        }

        /** The node for [mediaId], or null for a song ID or an ID this tree never produced. */
        fun parse(mediaId: String): BrowseId? {
            fixed[mediaId]?.let { return it }
            if (!mediaId.startsWith("/")) return null
            val parts = try {
                mediaId.substring(1).split('/').map(::decode)
            } catch (_: IllegalArgumentException) {
                return null // Malformed escapes: not one of ours.
            }
            return when {
                parts.size == 2 && parts[1].isNotEmpty() -> when (parts[0]) {
                    ALBUM -> Album(parts[1])
                    ARTIST -> Artist(parts[1])
                    PLAYLIST -> Playlist(parts[1])
                    GENRE -> Genre(parts[1])
                    else -> null
                }

                parts.size == 4 && parts[0] == TRACK && parts[1].isNotEmpty() -> {
                    val index = parts[2].toIntOrNull() ?: return null
                    val parent = if (parts[3].isEmpty()) null else parse(parts[3]) ?: return null
                    Track(parts[1], parent, index)
                }

                else -> null
            }
        }

        private fun path(vararg segments: String) = segments.joinToString("/", prefix = "/") { encode(it) }

        // The Charset overloads need API 33.
        private fun encode(segment: String): String = URLEncoder.encode(segment, UTF_8)

        private fun decode(segment: String): String = URLDecoder.decode(segment, UTF_8)

        private const val UTF_8 = "UTF-8"
    }
}
