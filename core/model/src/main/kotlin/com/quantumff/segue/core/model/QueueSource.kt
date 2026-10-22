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

    /**
     * Whether the source is [page]'s album, playlist or artist. IDs are unique per kind only, so the
     * kind counts too.
     */
    fun opens(page: SourcePage): Boolean = when (page) {
        is SourcePage.Album -> this is Album && id == page.albumId
        is SourcePage.Playlist -> this is Playlist && id == page.playlistId
        is SourcePage.Artist -> this is Artist && id == page.artistId
    }

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
    val kind: Kind,
    val id: String? = null,
    val name: String? = null,
    val shuffled: Boolean = false,
) {
    /** [QueueSource.Unknown] for a record this version cannot read: an unknown kind or a missing field. */
    fun toSource(): QueueSource = when (kind) {
        Kind.Album -> if (id != null && name != null) QueueSource.Album(id, name) else null
        Kind.Playlist -> if (id != null && name != null) QueueSource.Playlist(id, name) else null
        Kind.Artist -> if (id != null && name != null) QueueSource.Artist(id, name) else null
        Kind.Genre -> name?.let(QueueSource::Genre)
        Kind.Library -> QueueSource.Library(shuffled)
        Kind.InstantMix -> name?.let(QueueSource::InstantMix)
        Kind.Adopted -> name?.let(QueueSource::Adopted)
        Kind.Unknown -> null
    } ?: QueueSource.Unknown

    /** The variant of a [QueueSource]. [value] is what is stored and sent: keep it when renaming a kind. */
    enum class Kind(val value: String) {
        Album("Album"),
        Playlist("Playlist"),
        Artist("Artist"),
        Genre("Genre"),
        Library("Library"),
        InstantMix("InstantMix"),
        Adopted("Adopted"),
        Unknown("Unknown"),
        ;

        companion object {
            /** The kind stored as [value]; [Unknown] for one this version does not know. */
            fun of(value: String): Kind = entries.firstOrNull { it.value == value } ?: Unknown
        }
    }

    companion object {
        fun of(source: QueueSource): QueueSourceRecord = when (source) {
            is QueueSource.Album -> QueueSourceRecord(Kind.Album, source.id, source.name)
            is QueueSource.Playlist -> QueueSourceRecord(Kind.Playlist, source.id, source.name)
            is QueueSource.Artist -> QueueSourceRecord(Kind.Artist, source.id, source.name)
            is QueueSource.Genre -> QueueSourceRecord(Kind.Genre, name = source.name)
            is QueueSource.Library -> QueueSourceRecord(Kind.Library, shuffled = source.shuffled)
            is QueueSource.InstantMix -> QueueSourceRecord(Kind.InstantMix, name = source.seedName)
            is QueueSource.Adopted -> QueueSourceRecord(Kind.Adopted, name = source.deviceName)
            QueueSource.Unknown -> QueueSourceRecord(Kind.Unknown)
        }
    }
}
