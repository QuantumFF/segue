package com.quantumff.segue.feature.nowplaying

import androidx.compose.runtime.Immutable
import com.quantumff.segue.core.model.ArtistRef
import com.quantumff.segue.core.model.CurrentSong
import com.quantumff.segue.core.model.PlayerUiState
import com.quantumff.segue.core.model.QueueItem
import com.quantumff.segue.core.model.RepeatMode
import com.quantumff.segue.core.model.ReplayGain
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.SongFileInfo
import com.quantumff.segue.feature.nowplaying.lyrics.LyricsContent
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The panels that replace the art (phone) or fill the right pane (two-pane). */
enum class NowPlayingPanel {
    Lyrics,
    Queue,
    Related,
}

/** The current track as Now Playing shows it. */
@Immutable
data class NowPlayingTrack(
    val songId: String,
    /** Identifies the queue entry, so skipping to a duplicate of the same song still registers. */
    val timelineIndex: Int,
    val title: String,
    /** Display artist, shown when [artists] is empty. */
    val artist: String?,
    /** Credited artists, each a link. From the library; empty until it has the song. */
    val artists: ImmutableList<ArtistRef>,
    val album: String?,
    val albumId: String?,
    val coverArtId: String?,
    val isStarred: Boolean,
)

/** A queue neighbor the art swipe reveals and skips to. */
@Immutable
data class AdjacentArt(val coverArtId: String?, val timelineIndex: Int)

/**
 * Everything Now Playing shows except the position, which ticks separately so it recomposes only
 * the seekbar.
 */
@Immutable
data class NowPlayingUiState(
    /** Null while the queue is empty. */
    val track: NowPlayingTrack? = null,
    val previous: AdjacentArt? = null,
    val next: AdjacentArt? = null,
    val isPlaying: Boolean = false,
    /** The stream is loading: a ring surrounds play/pause. */
    val isBuffering: Boolean = false,
    val durationMillis: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val sleepTimer: SleepTimer? = null,
    /** "FLAC · 16/44.1" or "MP3 · 320"; null when the format is unknown. */
    val quality: String? = null,
    val details: TrackDetails? = null,
    /** Tracks in the queue, for "Clear queue". */
    val queueSize: Int = 0,
)

/** The details sheet. Missing values are null and their rows are left out. */
@Immutable
data class TrackDetails(
    val format: String?,
    val bitRateKbps: Int?,
    val samplingRateHz: Int?,
    val bitDepth: Int?,
    val channelCount: Int?,
    val sizeBytes: Long?,
    val replayGain: ReplayGain?,
    val path: String?,
)

/** The lyrics panel. */
@Immutable
sealed interface LyricsUiState {
    data object Loading : LyricsUiState

    /** [content] is null when the server has no lyrics for the song. */
    data class Loaded(val content: LyricsContent?) : LyricsUiState

    data object Error : LyricsUiState
}

/** The related panel: songs similar to the current track's artist. */
@Immutable
sealed interface RelatedUiState {
    data object Loading : RelatedUiState

    /** [currentSong] marks the current song's rows, as in other song lists. */
    data class Loaded(val songs: ImmutableList<Song>, val currentSong: CurrentSong? = null) : RelatedUiState

    /** The track has no known artist to find similar songs for. */
    data object Unavailable : RelatedUiState

    data object Error : RelatedUiState
}

/**
 * The related songs as shown while [current] is the current song: without it, since "related"
 * never means the same song, and with rows marked like other song lists.
 */
internal fun RelatedUiState.shownWhile(current: CurrentSong?): RelatedUiState = when (this) {
    is RelatedUiState.Loaded -> copy(
        songs = if (current == null) songs else songs.filterNot(current::isOf).toImmutableList(),
        currentSong = current,
    )

    else -> this
}

/** What a sleep timer choice in the sheet means. */
sealed interface SleepTimerChoice {
    data class Minutes(val minutes: Int) : SleepTimerChoice

    data object EndOfTrack : SleepTimerChoice

    data object EndOfAlbum : SleepTimerChoice
}

internal fun SleepTimerChoice.toSleepTimer(nowMillis: Long): SleepTimer = when (this) {
    is SleepTimerChoice.Minutes -> SleepTimer.AtTime(nowMillis + minutes * MILLIS_PER_MINUTE)
    SleepTimerChoice.EndOfTrack -> SleepTimer.EndOfTrack
    SleepTimerChoice.EndOfAlbum -> SleepTimer.EndOfAlbum
}

/** [song] and [fileInfo] are the library's and the server's view of the current track, if known. */
internal fun PlayerUiState.toNowPlayingUiState(song: Song?, fileInfo: SongFileInfo?): NowPlayingUiState {
    val item = currentItem ?: return NowPlayingUiState()
    val librarySong = song?.takeIf { it.id == item.songId }
    val file = fileInfo.takeIf { librarySong != null }
    return NowPlayingUiState(
        track = NowPlayingTrack(
            songId = item.songId,
            timelineIndex = item.timelineIndex,
            title = item.title,
            artist = item.artist ?: librarySong?.artist,
            artists = librarySong?.creditedArtists() ?: persistentListOf(),
            album = item.album,
            albumId = item.albumId,
            coverArtId = item.coverArtId,
            isStarred = item.isStarred,
        ),
        previous = neighbor(-1),
        next = neighbor(+1),
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        durationMillis = durationMillis,
        repeatMode = repeatMode,
        sleepTimer = sleepTimer,
        quality = librarySong?.let { qualityLabel(it.suffix, it.bitRate, file?.samplingRate, file?.bitDepth) },
        details = librarySong?.let { it.toDetails(file) },
        queueSize = queue.size,
    )
}

private fun Song.creditedArtists(): ImmutableList<ArtistRef> {
    if (artists.isNotEmpty()) return artists
    val id = artistId ?: return persistentListOf()
    val name = artist ?: return persistentListOf()
    return persistentListOf(ArtistRef(id, name))
}

private fun Song.toDetails(file: SongFileInfo?) = TrackDetails(
    format = suffix?.uppercase(Locale.ROOT) ?: contentType,
    bitRateKbps = bitRate?.takeIf { it > 0 },
    samplingRateHz = file?.samplingRate,
    bitDepth = file?.bitDepth,
    channelCount = file?.channelCount,
    sizeBytes = sizeBytes?.takeIf { it > 0 },
    replayGain = replayGain,
    path = file?.path,
)

/**
 * The quality pill (DESIGN.md §4.7): bit depth and sample rate for lossless files
 * ("FLAC · 16/44.1"), the bit rate for lossy ones ("MP3 · 320").
 */
internal fun qualityLabel(suffix: String?, bitRateKbps: Int?, samplingRateHz: Int?, bitDepth: Int?): String? {
    val format = suffix?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: return null
    val detail = when {
        bitDepth != null && bitDepth > 0 && samplingRateHz != null && samplingRateHz > 0 ->
            "$bitDepth/${formatKiloHertz(samplingRateHz)}"

        bitRateKbps != null && bitRateKbps > 0 -> bitRateKbps.toString()

        else -> null
    }
    return if (detail == null) format else "$format · $detail"
}

/** 44100 → "44.1", 48000 → "48". */
internal fun formatKiloHertz(hertz: Int): String {
    val tenths = (hertz + HERTZ_PER_TENTH_KHZ / 2) / HERTZ_PER_TENTH_KHZ
    val whole = tenths / TENTHS
    val fraction = tenths % TENTHS
    return if (fraction == 0) whole.toString() else "$whole.$fraction"
}

/** The neighbor in play order a swipe skips to; wraps around with repeat all. */
private fun PlayerUiState.neighbor(step: Int): AdjacentArt? {
    if (queue.size < 2 || currentQueueIndex !in queue.indices) return null
    var index = currentQueueIndex + step
    if (index !in queue.indices) {
        if (repeatMode != RepeatMode.All) return null
        index = (index + queue.size) % queue.size
    }
    val item: QueueItem = queue[index]
    return AdjacentArt(item.coverArtId, item.timelineIndex)
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val HERTZ_PER_TENTH_KHZ = 100
private const val TENTHS = 10
