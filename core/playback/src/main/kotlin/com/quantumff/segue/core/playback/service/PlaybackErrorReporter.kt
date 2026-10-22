package com.quantumff.segue.core.playback.service

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import com.quantumff.segue.core.model.PlaybackError
import com.quantumff.segue.core.model.PlaybackErrorKind

/**
 * The playback error the app shows ("Couldn't play <title>"), read from the player's error
 * callback, where the cause still carries the HTTP status: the song that was current, and why it
 * failed. It clears when playback moves to another item, or when a retry gets the player ready or
 * playing. [onChange] runs after each change, for the service to put
 * [error] in the session extras. Call on the player's thread.
 */
internal class PlaybackErrorReporter(private val player: Player, private val onChange: () -> Unit) : Player.Listener {
    var error: PlaybackError? = null
        private set

    override fun onPlayerError(error: PlaybackException) {
        val item = player.currentMediaItem ?: return
        report(PlaybackError(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), kindOf(error)))
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = report(null)

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) report(null)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) report(null)
    }

    private fun report(error: PlaybackError?) {
        if (error == this.error) return
        this.error = error
        onChange()
    }

    private companion object {
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_GONE = 410
        private const val HTTP_SERVER_ERRORS = 500

        fun kindOf(error: PlaybackException): PlaybackErrorKind = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT,
            // The URI resolver's failure to reach the server for a stream URL.
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            -> PlaybackErrorKind.Network

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode.let(::kindOfStatus)

            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackErrorKind.NotFound

            // A file the device cannot parse or decode: retrying would fail the same way.
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> PlaybackErrorKind.Unsupported

            else -> PlaybackErrorKind.Other
        }

        /** A 404 or 410 is a missing file; a timeout, throttling or a server error may pass, as a network error. */
        fun kindOfStatus(status: Int?): PlaybackErrorKind = when {
            status == HTTP_NOT_FOUND || status == HTTP_GONE -> PlaybackErrorKind.NotFound
            status == HTTP_REQUEST_TIMEOUT || status == HTTP_TOO_MANY_REQUESTS -> PlaybackErrorKind.Network
            status != null && status >= HTTP_SERVER_ERRORS -> PlaybackErrorKind.Network
            else -> PlaybackErrorKind.Other
        }
    }
}
