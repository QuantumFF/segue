package com.quantumff.segue.core.data.download

import com.quantumff.segue.core.database.model.DownloadEntity
import com.quantumff.segue.core.database.model.PinEntity
import com.quantumff.segue.core.database.model.PinSongRow
import com.quantumff.segue.core.model.DownloadState
import com.quantumff.segue.core.model.PinnedDownload

/** Bytes of a running download, kept in memory while it runs. */
internal data class LiveProgress(val bytes: Long, val total: Long?)

/** What reconciling the download rows with the pins changes. */
internal data class DownloadPlan(val toQueue: Set<String>, val toRemove: Set<String>)

/**
 * The pure rules of pinning. A song is kept while at least one pin covers it: the pins'
 * memberships are unioned ([desiredSongs]), so a song in two pins survives unpinning either one,
 * which is reference counting without a stored count that could drift.
 */
internal object DownloadPlanner {
    fun desiredSongs(membership: List<PinSongRow>): Set<String> = membership.mapTo(LinkedHashSet()) { it.songId }

    fun plan(desired: Set<String>, existing: Set<String>): DownloadPlan =
        DownloadPlan(toQueue = desired - existing, toRemove = existing - desired)

    /** Each pin with how much of it is on disk. [rows] and [live] are keyed by song ID. */
    fun pinStatuses(
        pins: List<PinEntity>,
        membership: List<PinSongRow>,
        rows: Map<String, DownloadEntity>,
        live: Map<String, LiveProgress>,
    ): List<PinnedDownload> {
        val songsByPin = membership.groupBy({ it.kind to it.itemId }, { it.songId })
        return pins.map { pin ->
            val songs = songsByPin[pin.kind to pin.itemId].orEmpty().distinct()
            var completed = 0
            var failed = 0
            var paused = 0
            var done = 0f
            var bytes = 0L
            songs.forEach { songId ->
                val row = rows[songId]
                when (row?.state) {
                    DownloadState.Completed -> {
                        completed++
                        done += 1f
                    }

                    DownloadState.Failed -> failed++

                    DownloadState.Paused -> paused++

                    else -> Unit
                }
                if (row != null && row.state != DownloadState.Completed) {
                    val partial = live[songId] ?: LiveProgress(row.bytesDownloaded, row.totalBytes)
                    bytes += partial.bytes
                    val total = partial.total
                    if (total != null && total > 0) done += (partial.bytes.toFloat() / total).coerceIn(0f, 1f)
                } else if (row != null) {
                    bytes += row.bytesDownloaded
                }
            }
            PinnedDownload(
                kind = pin.kind,
                id = pin.itemId,
                name = pin.name,
                coverArtId = pin.coverArtId,
                songCount = songs.size,
                completed = completed,
                failed = failed,
                paused = paused,
                progress = if (songs.isEmpty()) 0f else done / songs.size,
                bytes = bytes,
            )
        }
    }
}
