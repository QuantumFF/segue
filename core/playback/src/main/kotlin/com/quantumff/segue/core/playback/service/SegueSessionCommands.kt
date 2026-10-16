package com.quantumff.segue.core.playback.service

import android.os.Bundle
import androidx.media3.session.SessionCommand
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSourceRecord
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.SleepTimer

/** Custom session commands and session extras shared by the service and [com.quantumff.segue.core.playback.PlaybackController]. */
internal object SegueSessionCommands {
    private const val PREFIX = "com.quantumff.segue.playback."

    const val TOGGLE_FAVORITE = PREFIX + "TOGGLE_FAVORITE"
    const val SET_SLEEP_TIMER = PREFIX + "SET_SLEEP_TIMER"

    /** Restores the local queue an automatic remote adoption replaced (see QueueSyncEngine.takeAdoptionUndo). */
    const val UNDO_ADOPTION = PREFIX + "UNDO_ADOPTION"

    /** "Play albums" / "Shuffle albums": starts an album feed (see AlbumFeedController). Answers [ARG_STARTED]. */
    const val PLAY_ALBUMS = PREFIX + "PLAY_ALBUMS"

    const val ARG_SLEEP_KIND = "sleepKind"
    const val ARG_SLEEP_ENDS_AT = "sleepEndsAt"
    const val ARG_STARTED = "started"
    private const val ARG_SOURCE_KIND = "sourceKind"
    private const val ARG_SOURCE_ID = "sourceId"
    private const val ARG_SORT = "sort"
    private const val ARG_STARRED = "starredOnly"
    private const val ARG_GENRE = "genre"
    private const val ARG_YEAR = "year"
    private const val ARG_DOWNLOADED = "downloadedOnly"
    private const val ARG_ALBUM_ORDER = "albumOrder"
    private const val ARG_PLACEMENT = "placement"
    private const val ARG_SEED = "seed"

    private const val SLEEP_AT_TIME = "atTime"
    private const val SLEEP_END_OF_TRACK = "endOfTrack"
    private const val SLEEP_END_OF_ALBUM = "endOfAlbum"

    val all: List<SessionCommand> =
        listOf(
            TOGGLE_FAVORITE,
            SET_SLEEP_TIMER,
            UNDO_ADOPTION,
            PLAY_ALBUMS,
        )
            .map { SessionCommand(it, Bundle.EMPTY) }

    fun command(action: String) = SessionCommand(action, Bundle.EMPTY)

    fun sleepArgs(timer: SleepTimer?) = Bundle().apply {
        when (timer) {
            is SleepTimer.AtTime -> {
                putString(ARG_SLEEP_KIND, SLEEP_AT_TIME)
                putLong(ARG_SLEEP_ENDS_AT, timer.endsAtMillis)
            }

            SleepTimer.EndOfTrack -> putString(ARG_SLEEP_KIND, SLEEP_END_OF_TRACK)

            SleepTimer.EndOfAlbum -> putString(ARG_SLEEP_KIND, SLEEP_END_OF_ALBUM)

            null -> Unit
        }
    }

    fun sleepTimer(bundle: Bundle): SleepTimer? = when (bundle.getString(ARG_SLEEP_KIND)) {
        SLEEP_AT_TIME -> SleepTimer.AtTime(bundle.getLong(ARG_SLEEP_ENDS_AT))
        SLEEP_END_OF_TRACK -> SleepTimer.EndOfTrack
        SLEEP_END_OF_ALBUM -> SleepTimer.EndOfAlbum
        else -> null
    }

    fun playAlbumsArgs(request: AlbumPlayRequest, seed: Long) = Bundle().apply {
        putString(ARG_ALBUM_ORDER, request.order.name)
        putString(ARG_PLACEMENT, request.placement.name)
        putLong(ARG_SEED, seed)
        val source = AlbumSourceRecord.of(request.source)
        putString(ARG_SOURCE_KIND, source.kind)
        putString(ARG_SOURCE_ID, source.id)
        putString(ARG_SORT, source.sort)
        putBoolean(ARG_STARRED, source.starredOnly)
        putString(ARG_GENRE, source.genre)
        source.year?.let { putInt(ARG_YEAR, it) }
        putBoolean(ARG_DOWNLOADED, source.downloadedOnly)
    }

    /** The request and seed of [playAlbumsArgs], or null if [bundle] does not hold one. */
    fun playAlbums(bundle: Bundle): Pair<AlbumPlayRequest, Long>? {
        val source = AlbumSourceRecord(
            kind = bundle.getString(ARG_SOURCE_KIND) ?: return null,
            id = bundle.getString(ARG_SOURCE_ID),
            sort = bundle.getString(ARG_SORT),
            starredOnly = bundle.getBoolean(ARG_STARRED),
            genre = bundle.getString(ARG_GENRE),
            year = if (bundle.containsKey(ARG_YEAR)) bundle.getInt(ARG_YEAR) else null,
            downloadedOnly = bundle.getBoolean(ARG_DOWNLOADED),
        ).toSource() ?: return null
        val order = enumOrNull<AlbumOrder>(bundle.getString(ARG_ALBUM_ORDER)) ?: return null
        val placement = enumOrNull<QueuePlacement>(bundle.getString(ARG_PLACEMENT)) ?: return null
        return AlbumPlayRequest(source, order, placement) to bundle.getLong(ARG_SEED)
    }

    /** The answer to [PLAY_ALBUMS]: whether anything was queued. */
    fun startedResult(started: Boolean) = Bundle().apply { putBoolean(ARG_STARTED, started) }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        enumValues<E>().firstOrNull { it.name == name }

    /** Session extras: the sleep timer, which Media3's player state cannot carry. */
    fun extras(timer: SleepTimer?) = sleepArgs(timer)
}
