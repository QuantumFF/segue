package com.quantumff.segue.playback

import android.util.Log
import com.quantumff.segue.core.model.PlayActions

private const val TAG = "PlayActions"

/**
 * Play intents from every browse screen land here.
 *
 * TODO(playback-integration): route each callback to PlaybackController from core:playback.
 * They are deliberate no-ops until then (logged so the wiring can be checked on a device).
 */
internal fun placeholderPlayActions(): PlayActions = PlayActions(
    onPlaySongs = { songs, startIndex, shuffle ->
        // TODO(playback-integration): PlaybackController.setQueue(songs, startIndex, shuffle) and play.
        Log.d(TAG, "playSongs(${songs.size} songs, start=$startIndex, shuffle=$shuffle) not wired yet")
    },
    onShuffleAlbums = { source ->
        // TODO(playback-integration): resolve the source's albums and start in ShuffleMode.Albums.
        Log.d(TAG, "shuffleAlbums($source) not wired yet")
    },
    onPlayNext = { songs ->
        // TODO(playback-integration): insert after the current queue item.
        Log.d(TAG, "playNext(${songs.size} songs) not wired yet")
    },
    onAddToQueue = { songs ->
        // TODO(playback-integration): append to the queue.
        Log.d(TAG, "addToQueue(${songs.size} songs) not wired yet")
    },
    onStartInstantMix = { songId ->
        // TODO(playback-integration): getSimilarSongs2 for the song, then play.
        Log.d(TAG, "instantMix($songId) not wired yet")
    },
)
