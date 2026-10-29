package com.quantumff.segue.feature.playlist

import com.quantumff.segue.core.model.Song

/**
 * Copy [copy] (from 0) of song [songId] in a playlist's saved order, which can list a song more
 * than once. Unlike a position, it still names the same row after other songs are removed.
 */
internal data class SongCopy(val songId: String, val copy: Int) {
    /** Its position in [songIds], a saved order, or -1 if they have fewer copies of the song. */
    fun positionIn(songIds: List<String>): Int {
        var seen = 0
        return songIds.indexOfFirst { it == songId && seen++ == copy }
    }

    /** This copy once [removed] is gone from the saved order: a later copy of the same song moves up one. */
    fun afterRemovalOf(removed: SongCopy): SongCopy =
        if (removed.songId == songId && copy > removed.copy) copy(copy = copy - 1) else this

    /** Whether [entry] is this copy. */
    fun isOf(entry: PlaylistEntry): Boolean = entry.song.id == songId && entry.copy == copy
}

/** [this] saved order as entries, each numbered among the copies of its song. */
internal fun List<Song>.toEntries(): List<PlaylistEntry> {
    val copies = mutableMapOf<String, Int>()
    return map { song ->
        val copy = copies.getOrDefault(song.id, 0)
        copies[song.id] = copy + 1
        PlaylistEntry(song, copy)
    }
}
