package com.quantumff.segue.core.model

/**
 * Where the queue came from (the queue source): what the last play intent that replaced the queue
 * played, or the device whose queue was adopted. Play next and Add to queue leave it alone, so it
 * names the queue that was played even after songs are added to it.
 */
sealed interface QueueSource {
    /** What the source is called, such as the album's name; null when it has no name of its own. */
    val name: String?

    /**
     * The ID of the page the source opens: the album, playlist or artist ID, or the genre's name.
     * Null when it opens none.
     */
    val id: String?

    data class Album(override val id: String, override val name: String) : QueueSource

    data class Playlist(override val id: String, override val name: String) : QueueSource

    /** An artist's top songs or albums. */
    data class Artist(override val id: String, override val name: String) : QueueSource

    /** A genre's albums, by name: the genre page it opens is named too. */
    data class Genre(override val name: String) : QueueSource {
        override val id: String get() = name
    }

    /** The whole library or the Library's albums; [shuffled] when shuffled as it was queued. */
    data class Library(val shuffled: Boolean) : QueueSource {
        override val name: String? get() = null
        override val id: String? get() = null
    }

    /** An instant mix started from the song or artist [seedName]. */
    data class InstantMix(val seedName: String) : QueueSource {
        override val name: String get() = seedName
        override val id: String? get() = null
    }

    /** Another device's queue, adopted from [deviceName]. */
    data class Adopted(val deviceName: String) : QueueSource {
        override val name: String get() = deviceName
        override val id: String? get() = null
    }

    /** Nothing says where the queue came from, such as songs queued from search results. */
    data object Unknown : QueueSource {
        override val name: String? get() = null
        override val id: String? get() = null
    }
}

/**
 * A [QueueSource] flattened to plain fields, to carry it to the playback service and back and to
 * save it. [kind] names the variant; [id] and [name] are the source's own, and [shuffled] is a
 * library source's.
 */
data class QueueSourceRecord(
    val kind: String,
    val id: String? = null,
    val name: String? = null,
    val shuffled: Boolean = false,
) {
    /** [QueueSource.Unknown] for a record this version cannot read: an unknown kind or a missing field. */
    fun toSource(): QueueSource = when (kind) {
        KIND_ALBUM -> if (id != null && name != null) QueueSource.Album(id, name) else null
        KIND_PLAYLIST -> if (id != null && name != null) QueueSource.Playlist(id, name) else null
        KIND_ARTIST -> if (id != null && name != null) QueueSource.Artist(id, name) else null
        KIND_GENRE -> name?.let(QueueSource::Genre)
        KIND_LIBRARY -> QueueSource.Library(shuffled)
        KIND_INSTANT_MIX -> name?.let(QueueSource::InstantMix)
        KIND_ADOPTED -> name?.let(QueueSource::Adopted)
        else -> null
    } ?: QueueSource.Unknown

    companion object {
        private const val KIND_ALBUM = "Album"
        private const val KIND_PLAYLIST = "Playlist"
        private const val KIND_ARTIST = "Artist"
        private const val KIND_GENRE = "Genre"
        private const val KIND_LIBRARY = "Library"
        private const val KIND_INSTANT_MIX = "InstantMix"
        private const val KIND_ADOPTED = "Adopted"
        private const val KIND_UNKNOWN = "Unknown"

        fun of(source: QueueSource): QueueSourceRecord = when (source) {
            is QueueSource.Album -> QueueSourceRecord(KIND_ALBUM, source.id, source.name)
            is QueueSource.Playlist -> QueueSourceRecord(KIND_PLAYLIST, source.id, source.name)
            is QueueSource.Artist -> QueueSourceRecord(KIND_ARTIST, source.id, source.name)
            is QueueSource.Genre -> QueueSourceRecord(KIND_GENRE, name = source.name)
            is QueueSource.Library -> QueueSourceRecord(KIND_LIBRARY, shuffled = source.shuffled)
            is QueueSource.InstantMix -> QueueSourceRecord(KIND_INSTANT_MIX, name = source.seedName)
            is QueueSource.Adopted -> QueueSourceRecord(KIND_ADOPTED, name = source.deviceName)
            QueueSource.Unknown -> QueueSourceRecord(KIND_UNKNOWN)
        }
    }
}
