package com.quantumff.segue.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What can be pinned for offline use. An artist pin covers all of the artist's albums. */
enum class PinKind {
    Album,
    Playlist,
    Artist,
    Song,
}

/**
 * How much space downloads may use (DESIGN.md §4.10, "storage limit"). Once reached, queued
 * songs wait: no new download starts until space is freed or the limit is raised. Decimal
 * gigabytes, as Android shows file sizes.
 */
enum class StorageLimit(val gigabytes: Int?) {
    None(null),
    Gb2(2),
    Gb5(5),
    Gb10(10),
    Gb20(20),
    ;

    val bytes: Long? get() = gigabytes?.let { it * BYTES_PER_GB }

    /** Whether downloads using [usedBytes] may not grow further. */
    fun isReachedBy(usedBytes: Long): Boolean = bytes?.let { usedBytes >= it } ?: false

    private companion object {
        const val BYTES_PER_GB = 1_000_000_000L
    }
}

/** Download settings (DESIGN.md §4.10, "Downloads & storage"). */
data class DownloadPreferences(
    /** Original file, or a transcode. Applies to downloads queued after the change. */
    val quality: StreamingQuality = StreamingQuality.Original,
    /** Download only on an unmetered network. */
    val wifiOnly: Boolean = true,
    /** Download only while charging. */
    val chargingOnly: Boolean = false,
    /** Manual offline mode: behave as offline even with a network. */
    val forceOffline: Boolean = false,
    val storageLimit: StorageLimit = StorageLimit.None,
)

/** How far the download of one song, or of everything a pin covers, has got. */
sealed interface DownloadStatus {
    data object Queued : DownloadStatus

    /** [progress] is 0..1. */
    data class Downloading(val progress: Float) : DownloadStatus

    data object Paused : DownloadStatus

    /** At least one song failed and nothing is left to download. */
    data object Failed : DownloadStatus

    data object Downloaded : DownloadStatus
}

/** One song's download, for the badge on its row. */
data class SongDownload(val songId: String, val state: DownloadState, val progress: Float) {
    val status: DownloadStatus
        get() = when (state) {
            DownloadState.Queued -> DownloadStatus.Queued
            DownloadState.Downloading -> DownloadStatus.Downloading(progress)
            DownloadState.Paused -> DownloadStatus.Paused
            DownloadState.Completed -> DownloadStatus.Downloaded
            DownloadState.Failed -> DownloadStatus.Failed
        }
}

/** A pinned album, playlist, artist or song and how much of it is on the device. */
data class PinnedDownload(
    val kind: PinKind,
    val id: String,
    val name: String,
    val coverArtId: String?,
    /** Distinct songs the pin covers. */
    val songCount: Int,
    val completed: Int,
    val failed: Int,
    val paused: Int,
    /** Fraction of the songs on disk, counting partial downloads by their bytes. */
    val progress: Float,
    /** Bytes on disk, partial downloads included. */
    val bytes: Long,
) {
    val status: DownloadStatus
        get() = when {
            songCount > 0 && completed == songCount -> DownloadStatus.Downloaded
            failed > 0 && completed + failed == songCount -> DownloadStatus.Failed
            paused > 0 && completed + failed + paused == songCount -> DownloadStatus.Paused
            progress > 0f -> DownloadStatus.Downloading(progress)
            else -> DownloadStatus.Queued
        }

    val isComplete: Boolean get() = status == DownloadStatus.Downloaded
}

/** Everything the Downloads screen shows. */
data class DownloadOverview(
    val pins: ImmutableList<PinnedDownload> = persistentListOf(),
    /** Bytes of finished and partial downloads on disk. */
    val usedBytes: Long = 0,
    /** Free bytes on the volume downloads are stored on. */
    val freeBytes: Long = 0,
    val storageLimit: StorageLimit = StorageLimit.None,
    /** Downloads on this device (all servers) fill [storageLimit]: queued songs wait. */
    val limitReached: Boolean = false,
)
