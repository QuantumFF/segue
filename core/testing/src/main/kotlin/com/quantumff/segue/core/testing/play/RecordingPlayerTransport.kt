package com.quantumff.segue.core.testing.play

import com.quantumff.segue.core.data.play.PlayerTransport

/** Records the transport commands a screen sends, in order, without playing anything. */
class RecordingPlayerTransport : PlayerTransport {
    enum class Command { Play, Pause }

    val commands = mutableListOf<Command>()

    override fun play() {
        commands += Command.Play
    }

    override fun pause() {
        commands += Command.Pause
    }
}
