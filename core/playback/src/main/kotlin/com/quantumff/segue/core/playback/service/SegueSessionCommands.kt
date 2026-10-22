package com.quantumff.segue.core.playback.service

import android.os.Bundle
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.MediaItem
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.quantumff.segue.core.model.AlbumOrder
import com.quantumff.segue.core.model.AlbumPlayRequest
import com.quantumff.segue.core.model.AlbumSourceRecord
import com.quantumff.segue.core.model.QueuePlacement
import com.quantumff.segue.core.model.SleepTimer
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.playback.cache.StreamRequest

/** Custom session commands and session extras shared by the service and [com.quantumff.segue.core.playback.PlaybackController]. */
internal object SegueSessionCommands {
    private const val PREFIX = "com.quantumff.segue.playback."

    const val TOGGLE_FAVORITE = PREFIX + "TOGGLE_FAVORITE"
    const val SET_SLEEP_TIMER = PREFIX + "SET_SLEEP_TIMER"

    /**
     * Adopts the remote queue queue sync staged (QueueSyncEngine.acceptOffer or an automatic
     * adoption), paused; see SavedQueueLoader.adopt. With [adoptArgs] `play`, it then plays. Answers
     * [ARG_STARTED].
     */
    const val ADOPT_QUEUE = PREFIX + "ADOPT_QUEUE"

    /** Restores the local queue an automatic remote adoption replaced (see SavedQueueLoader.undoAdoption). */
    const val UNDO_ADOPTION = PREFIX + "UNDO_ADOPTION"

    /** "Play albums" / "Shuffle albums" / "Shuffle all", queued by AlbumPlays. Answers [ARG_STARTED]. */
    const val PLAY_ALBUMS = PREFIX + "PLAY_ALBUMS"

    /** "Play", "Play next", "Add to queue" of songs: queued by QueueEditor. Answers [ARG_STARTED]. */
    const val QUEUE_SONGS = PREFIX + "QUEUE_SONGS"

    /**
     * Puts back the queue item the user just removed (QueueEditor.putBack). Not a play intent: it is
     * the user's own queue, so it is not narrowed offline.
     */
    const val UNDO_REMOVE = PREFIX + "UNDO_REMOVE"

    const val ARG_SLEEP_KIND = "sleepKind"
    const val ARG_SLEEP_ENDS_AT = "sleepEndsAt"
    const val ARG_STARTED = "started"
    private const val ARG_SOURCE_KIND = "sourceKind"
    private const val ARG_SOURCE_ID = "sourceId"
    private const val ARG_SORT = "sort"
    private const val ARG_STARRED = "starred"
    private const val ARG_GENRE = "genre"
    private const val ARG_YEAR = "year"
    private const val ARG_DOWNLOADED = "downloaded"
    private const val ARG_ALBUM_ORDER = "albumOrder"
    private const val ARG_PLACEMENT = "placement"
    private const val ARG_ITEMS = "items"
    private const val ARG_START_INDEX = "startIndex"
    private const val ARG_SHUFFLE = "shuffle"
    private const val ARG_ITEM = "item"
    private const val ARG_INDEX = "index"
    private const val ARG_PLAY = "play"

    private const val ARG_STREAM_SONG_ID = "streamSongId"
    private const val ARG_STREAM_SIZE = "streamSize"
    private const val ARG_STREAM_MAX_BIT_RATE = "streamMaxBitRate"
    private const val ARG_STREAM_FORMAT = "streamFormat"

    private const val SLEEP_AT_TIME = "atTime"
    private const val SLEEP_END_OF_TRACK = "endOfTrack"
    private const val SLEEP_END_OF_ALBUM = "endOfAlbum"

    val all: List<SessionCommand> =
        listOf(
            TOGGLE_FAVORITE,
            SET_SLEEP_TIMER,
            ADOPT_QUEUE,
            UNDO_ADOPTION,
            PLAY_ALBUMS,
            QUEUE_SONGS,
            UNDO_REMOVE,
        )
            .map { SessionCommand(it, Bundle.EMPTY) }

    fun command(action: String) = SessionCommand(action, Bundle.EMPTY)

    /** [ADOPT_QUEUE]'s args: whether to play the adopted queue. [adoptPlays] reads it back. */
    fun adoptArgs(play: Boolean) = Bundle().apply { putBoolean(ARG_PLAY, play) }

    fun adoptPlays(args: Bundle): Boolean = args.getBoolean(ARG_PLAY)

    /**
     * [SET_SLEEP_TIMER]'s args, and the session extras: the sleep timer, which Media3's player state
     * cannot carry. [sleepTimer] reads either back.
     */
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

    /**
     * Session extras: what the player asked for when it opened [songId] (see
     * [com.quantumff.segue.core.playback.cache.SegueUriResolver]), which Media3's tracks cannot
     * carry. [streamRequest] reads it back.
     */
    fun streamRequestArgs(songId: String, request: StreamRequest) = Bundle().apply {
        putString(ARG_STREAM_SONG_ID, songId)
        when (request) {
            is StreamRequest.Download -> putLong(ARG_STREAM_SIZE, request.sizeBytes)

            is StreamRequest.Stream -> {
                putInt(ARG_STREAM_MAX_BIT_RATE, request.quality.maxBitRateKbps)
                putString(ARG_STREAM_FORMAT, request.quality.format)
            }
        }
    }

    /** The request of [streamRequestArgs] if it is for [songId], else null. */
    fun streamRequest(bundle: Bundle, songId: String): StreamRequest? = when {
        bundle.getString(ARG_STREAM_SONG_ID) != songId -> null

        bundle.containsKey(ARG_STREAM_SIZE) -> StreamRequest.Download(bundle.getLong(ARG_STREAM_SIZE))

        else -> StreamRequest.Stream(
            StreamingQuality(bundle.getInt(ARG_STREAM_MAX_BIT_RATE), bundle.getString(ARG_STREAM_FORMAT)),
        )
    }

    fun playAlbumsArgs(request: AlbumPlayRequest) = Bundle().apply {
        putString(ARG_ALBUM_ORDER, request.order.name)
        putString(ARG_PLACEMENT, request.placement.name)
        val source = AlbumSourceRecord.of(request.source)
        putString(ARG_SOURCE_KIND, source.kind)
        putString(ARG_SOURCE_ID, source.id)
        putString(ARG_SORT, source.sort)
        source.starred?.let { putBoolean(ARG_STARRED, it) }
        putString(ARG_GENRE, source.genre)
        source.year?.let { putInt(ARG_YEAR, it) }
        source.downloaded?.let { putBoolean(ARG_DOWNLOADED, it) }
    }

    /** The request of [playAlbumsArgs], or null if [bundle] does not hold one. */
    fun playAlbums(bundle: Bundle): AlbumPlayRequest? {
        val source = AlbumSourceRecord(
            kind = bundle.getString(ARG_SOURCE_KIND) ?: return null,
            id = bundle.getString(ARG_SOURCE_ID),
            sort = bundle.getString(ARG_SORT),
            starred = if (bundle.containsKey(ARG_STARRED)) bundle.getBoolean(ARG_STARRED) else null,
            genre = bundle.getString(ARG_GENRE),
            year = if (bundle.containsKey(ARG_YEAR)) bundle.getInt(ARG_YEAR) else null,
            downloaded = if (bundle.containsKey(ARG_DOWNLOADED)) bundle.getBoolean(ARG_DOWNLOADED) else null,
        ).toSource() ?: return null
        val order = enumOrNull<AlbumOrder>(bundle.getString(ARG_ALBUM_ORDER)) ?: return null
        val placement = enumOrNull<QueuePlacement>(bundle.getString(ARG_PLACEMENT)) ?: return null
        return AlbumPlayRequest(source, order, placement)
    }

    /** A [QUEUE_SONGS] request: [items] at [placement], from [startIndex] or with [shuffle]. */
    data class QueueSongs(
        val items: List<MediaItem>,
        val placement: QueuePlacement,
        val startIndex: Int,
        val shuffle: Boolean,
    )

    fun queueSongsArgs(request: QueueSongs) = Bundle().apply {
        // Read in chunks through a binder, as Media3 sends setMediaItems: a long list would not fit
        // in one transaction.
        putBinder(ARG_ITEMS, BundleListRetriever(ImmutableList.copyOf(request.items.map { it.toBundle() })))
        putString(ARG_PLACEMENT, request.placement.name)
        putInt(ARG_START_INDEX, request.startIndex)
        putBoolean(ARG_SHUFFLE, request.shuffle)
    }

    /** The request of [queueSongsArgs], or null if [bundle] does not hold one. */
    fun queueSongs(bundle: Bundle): QueueSongs? {
        val items = bundle.getBinder(ARG_ITEMS) ?: return null
        val placement = enumOrNull<QueuePlacement>(bundle.getString(ARG_PLACEMENT)) ?: return null
        return QueueSongs(
            BundleListRetriever.getList(items).map(MediaItem::fromBundle),
            placement,
            bundle.getInt(ARG_START_INDEX),
            bundle.getBoolean(ARG_SHUFFLE),
        )
    }

    /** An [UNDO_REMOVE] request: [item] goes back at queue position [index]. */
    data class Removed(val item: MediaItem, val index: Int)

    fun undoRemoveArgs(removed: Removed) = Bundle().apply {
        putBundle(ARG_ITEM, removed.item.toBundle())
        putInt(ARG_INDEX, removed.index)
    }

    /** The request of [undoRemoveArgs], or null if [bundle] does not hold one. */
    fun undoRemove(bundle: Bundle): Removed? {
        val item = bundle.getBundle(ARG_ITEM) ?: return null
        return Removed(MediaItem.fromBundle(item), bundle.getInt(ARG_INDEX))
    }

    /** The answer to [PLAY_ALBUMS], [QUEUE_SONGS] and [ADOPT_QUEUE]: whether anything was queued. */
    fun startedResult(started: Boolean) = Bundle().apply { putBoolean(ARG_STARTED, started) }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
