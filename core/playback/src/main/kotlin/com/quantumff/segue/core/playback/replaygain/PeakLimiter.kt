package com.quantumff.segue.core.playback.replaygain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Keeps boosted audio from clipping when the track's peak is unknown. A frame that would exceed
 * [THRESHOLD] pulls the gain down at once (no look-ahead, so nothing is delayed); the gain then
 * recovers smoothly over about [RELEASE_SECONDS]. All channels share one gain, which keeps the
 * stereo image. Below the threshold the signal passes unchanged.
 *
 * Not thread-safe: the audio playback thread owns it.
 */
class PeakLimiter {
    private var reduction = 1f
    private var release = 1f

    /** Call when the sample rate is known, before [process]. */
    fun configure(sampleRate: Int) {
        release = 1f - exp(-1.0 / (RELEASE_SECONDS * sampleRate)).toFloat()
    }

    /** Back to no gain reduction, for a new stream or after a seek. */
    fun reset() {
        reduction = 1f
    }

    /** Limits the first [size] interleaved [samples] of [channels] channels, in place. */
    fun process(samples: FloatArray, size: Int, channels: Int) {
        var frame = 0
        while (frame < size) {
            var peak = 0f
            for (channel in frame until frame + channels) peak = max(peak, abs(samples[channel]))
            reduction += (1f - reduction) * release
            if (peak * reduction > THRESHOLD) reduction = THRESHOLD / peak
            for (channel in frame until frame + channels) samples[channel] *= reduction
            frame += channels
        }
    }

    companion object {
        /** About -0.2 dBFS: a little room for the conversion back to 16-bit. */
        const val THRESHOLD = 0.98f
        const val RELEASE_SECONDS = 0.1
    }
}
