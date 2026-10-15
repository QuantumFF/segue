package com.quantumff.segue.core.playback.service

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.SessionCommand
import com.quantumff.segue.core.model.ShuffleMode
import com.quantumff.segue.core.model.SleepTimer

/** Custom session commands and session extras shared by the service and [com.quantumff.segue.core.playback.PlaybackController]. */
internal object SegueSessionCommands {
    private const val PREFIX = "com.quantumff.segue.playback."

    const val CYCLE_SHUFFLE = PREFIX + "CYCLE_SHUFFLE"
    const val SET_SHUFFLE = PREFIX + "SET_SHUFFLE"
    const val TOGGLE_FAVORITE = PREFIX + "TOGGLE_FAVORITE"
    const val SET_SLEEP_TIMER = PREFIX + "SET_SLEEP_TIMER"
    const val MOVE_IN_PLAY_ORDER = PREFIX + "MOVE_IN_PLAY_ORDER"
    const val REINSERT = PREFIX + "REINSERT"

    const val ARG_SHUFFLE_MODE = "shuffleMode"
    const val ARG_SLEEP_KIND = "sleepKind"
    const val ARG_SLEEP_ENDS_AT = "sleepEndsAt"
    const val ARG_FROM = "from"
    const val ARG_TO = "to"
    const val ARG_COUNT = "count"
    const val ARG_ITEM = "item"
    const val ARG_TIMELINE_INDEX = "timelineIndex"
    const val ARG_PLAY_POSITION = "playPosition"

    private const val SLEEP_AT_TIME = "atTime"
    private const val SLEEP_END_OF_TRACK = "endOfTrack"
    private const val SLEEP_END_OF_ALBUM = "endOfAlbum"

    val all: List<SessionCommand> =
        listOf(CYCLE_SHUFFLE, SET_SHUFFLE, TOGGLE_FAVORITE, SET_SLEEP_TIMER, MOVE_IN_PLAY_ORDER, REINSERT)
            .map { SessionCommand(it, Bundle.EMPTY) }

    fun command(action: String) = SessionCommand(action, Bundle.EMPTY)

    fun shuffleArgs(mode: ShuffleMode) = Bundle().apply { putString(ARG_SHUFFLE_MODE, mode.name) }

    fun shuffleMode(bundle: Bundle): ShuffleMode? =
        bundle.getString(ARG_SHUFFLE_MODE)?.let { name -> ShuffleMode.entries.firstOrNull { it.name == name } }

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

    fun moveArgs(from: Int, to: Int, count: Int = 1) = Bundle().apply {
        putInt(ARG_FROM, from)
        putInt(ARG_TO, to)
        putInt(ARG_COUNT, count)
    }

    /** Puts [item] back at queue index [timelineIndex] and play position [playPosition] (undo of a removal). */
    fun reinsertArgs(item: MediaItem, timelineIndex: Int, playPosition: Int) = Bundle().apply {
        putBundle(ARG_ITEM, item.toBundle())
        putInt(ARG_TIMELINE_INDEX, timelineIndex)
        putInt(ARG_PLAY_POSITION, playPosition)
    }

    fun reinsertArgs(bundle: Bundle): Triple<MediaItem, Int, Int>? {
        val item = bundle.getBundle(ARG_ITEM)?.let(MediaItem::fromBundle) ?: return null
        return Triple(item, bundle.getInt(ARG_TIMELINE_INDEX), bundle.getInt(ARG_PLAY_POSITION))
    }

    /** Session extras: the three-way shuffle mode and the sleep timer, which Media3's player state cannot carry. */
    fun extras(mode: ShuffleMode, timer: SleepTimer?) = Bundle(sleepArgs(timer)).apply {
        putString(ARG_SHUFFLE_MODE, mode.name)
    }
}
